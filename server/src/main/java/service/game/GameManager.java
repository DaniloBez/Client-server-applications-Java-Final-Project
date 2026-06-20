package service.game;

import static dto.Commands.INVALID_MOVE;

import dto.Commands;
import dto.Message;
import dto.response.ErrorResponse;
import dto.response.MatchEndedResponse;
import dto.response.MatchFoundResponse;
import dto.response.PlayerMoveResponse;
import dto.response.RoundEndedResponse;
import entity.Match;
import entity.MatchStatus;
import entity.User;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import repository.MatchRepository;
import repository.UserRepository;
import tools.jackson.databind.json.JsonMapper;
import utils.EloSystem;

@Slf4j
public class GameManager {
    private final ConcurrentHashMap<Long, GameSession> activeGames = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Long> playerToGame = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private final JsonMapper mapper = new JsonMapper();

    private static final AtomicLong messageId = new AtomicLong();

    private final UserRepository userRepository;
    private final MatchRepository matchRepository;

    @Setter
    private Consumer<Message> messageDispatcher;

    public GameManager(
            UserRepository userRepository,
            MatchRepository matchRepository
    ) {
        this.userRepository = userRepository;
        this.matchRepository = matchRepository;

        scheduler.scheduleAtFixedRate(
                this::checkTimeouts,
                1,
                1,
                TimeUnit.SECONDS
        );
    }

    public void stop() {
        log.info("Stopping GameManager scheduler");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS))
                scheduler.shutdownNow();
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public List<Message> createGame(int player1Id, int player2Id) {
        long matchId = matchRepository.create(player1Id, player2Id);

        GameSession gameSession = new GameSession(matchId, player1Id, player2Id);
        activeGames.put(matchId, gameSession);
        playerToGame.put(player1Id, matchId);
        playerToGame.put(player2Id, matchId);

        User user1 = userRepository.getUser(player1Id).orElseThrow();
        User user2 = userRepository.getUser(player2Id).orElseThrow();
        
        log.info("Game created with matchId {} for players {} and {}", 
                matchId, player1Id, player2Id);

        List<Message> responses = new ArrayList<>();
        responses.add(buildMessage(
                user1.getId(),
                Commands.MATCH_FOUND,
                new MatchFoundResponse(
                        user2.getUsername(),
                        user2.getEloRating(),
                        gameSession.isFirstPlayerStartGame(),
                        gameSession.isFirstPlayerStartGame()
                )
        ));

        responses.add(buildMessage(
                user2.getId(),
                Commands.MATCH_FOUND,
                new MatchFoundResponse(
                        user1.getUsername(),
                        user1.getEloRating(),
                        !gameSession.isFirstPlayerStartGame(),
                        !gameSession.isFirstPlayerStartGame()
                )
        ));

        return responses;
    }

    public List<Message> handleMove(int userId, byte row, byte col) {
        if (!playerToGame.containsKey(userId))
            throw new IllegalArgumentException("User not have active game!");

        long matchId = playerToGame.get(userId);
        GameSession gameSession = activeGames.get(matchId);
        if (gameSession == null) {
            log.warn("Attempt to move in non-existent or finished match {}", matchId);
            return List.of();
        }

        log.debug("Player {} made move ({}, {}) in match {}", userId, row, col, matchId);

        MoveResult result = gameSession.processMove(userId, row, col);

        List<Message> responses = new ArrayList<>();

        switch (result) {
            case INVALID -> {
                log.info("Invalid move by player {} in match {}", userId, matchId);
                responses.add(buildMessage(
                        userId,
                        INVALID_MOVE,
                        new ErrorResponse("Недійсний хід!", "Ваш хід недійсний!")
                ));
            }
            case SUCCESS -> responses.addAll(buildMoveMessages(gameSession, userId, row, col));
            case ROUND_WIN, DRAW -> {
                log.info("Round finished in match {} (DRAW: {})", matchId, 
                        result == MoveResult.DRAW);
                responses.addAll(buildMoveMessages(gameSession, userId, row, col));
                gameSession.resetBoard();
                responses.addAll(buildRoundEndedMessages(
                        gameSession, userId, result == MoveResult.DRAW
                ));
            }
            case MATCH_WIN, MATCH_END -> {
                log.info("Match {} finished (DRAW: {})", matchId, 
                        result == MoveResult.MATCH_END);
                responses.addAll(buildMoveMessages(gameSession, userId, row, col));
                boolean isDraw = (result == MoveResult.MATCH_END);
                responses.addAll(handleMatchFinished(gameSession, isDraw ? -1 : userId));
            }
            default -> throw new IllegalStateException("Unexpected value: " + result);
        }
        return responses;
    }

    private void checkTimeouts() {
        long now = System.currentTimeMillis();
        for (GameSession session : activeGames.values()) {
            if (now - session.getLastMoveTimestamp() > 60_000) {
                log.warn("Match {} timed out due to inactivity", session.getMatchId());
                int playerToForfeit = session.isFirstPlayerMove()
                        ? session.getPlayer1Id()
                        : session.getPlayer2Id();

                List<Message> disconnectMessages = handleDisconnect(playerToForfeit);
                if (messageDispatcher != null) {
                    for (Message m : disconnectMessages)
                        messageDispatcher.accept(m);
                }
            }
        }
    }

    public List<Message> handleDisconnect(int disconnectedUserId) {
        if (!playerToGame.containsKey(disconnectedUserId))
            return List.of();

        long matchId = playerToGame.get(disconnectedUserId);
        GameSession session = activeGames.get(matchId);
        if (session == null) {
            return List.of();
        }

        log.info("Handling disconnect for player {} in match {}", disconnectedUserId, matchId);

        int player1 = session.getPlayer1Id();
        int player2 = session.getPlayer2Id();

        int winnerId = (disconnectedUserId == player1) ? player2 : player1;

        User disconnectedUser = userRepository.getUser(disconnectedUserId).orElseThrow();
        User winnerUser = userRepository.getUser(winnerId).orElseThrow();

        int eloDeltaWinner = EloSystem.calculateEloDelta(
                winnerUser.getEloRating(),
                disconnectedUser.getEloRating(),
                winnerUser.getMatchCount(),
                true,
                false,
                3,
                0
        );

        int eloDeltaLoser = EloSystem.calculateEloDelta(
                disconnectedUser.getEloRating(),
                winnerUser.getEloRating(),
                disconnectedUser.getMatchCount(),
                false,
                false,
                0,
                3
        );

        userRepository.updateEloAndMatchCount(winnerId, eloDeltaWinner);
        userRepository.updateEloAndMatchCount(disconnectedUserId, eloDeltaLoser);

        Match match = new Match(
                matchId,
                player1,
                player2,
                session.getPlayer1Score(),
                session.getPlayer2Score(),
                winnerId,
                MatchStatus.TECHNICAL_WIN
        );

        matchRepository.save(match);

        List<Message> responses = new ArrayList<>();
        responses.add(buildMessage(
                winnerId,
                Commands.MATCH_ENDED,
                new MatchEndedResponse(
                        true,
                        (byte) 3,
                        (byte) 0,
                        eloDeltaWinner
                )
        ));

        activeGames.remove(matchId);
        playerToGame.remove(player1);
        playerToGame.remove(player2);

        return responses;
    }

    private List<Message> buildRoundEndedMessages(
            GameSession gameSession, int winnerId, boolean isDraw
    ) {
        int player1 = gameSession.getPlayer1Id();
        int player2 = gameSession.getPlayer2Id();

        boolean isP1NextTurn = gameSession.isFirstPlayerMove();

        List<Message> responses = new ArrayList<>();
        responses.add(buildMessage(player1, Commands.ROUND_ENDED, new RoundEndedResponse(
                isDraw ? null : (player1 == winnerId),
                gameSession.getPlayer1Score(),
                gameSession.getPlayer2Score(),
                isP1NextTurn
        )));

        responses.add(buildMessage(player2, Commands.ROUND_ENDED, new RoundEndedResponse(
                isDraw ? null : (player2 == winnerId),
                gameSession.getPlayer2Score(),
                gameSession.getPlayer1Score(),
                !isP1NextTurn
        )));
        
        return responses;
    }

    private List<Message> handleMatchFinished(GameSession session, int winnerId) {
        int player1 = session.getPlayer1Id();
        int player2 = session.getPlayer2Id();
        boolean isDraw = (winnerId == -1);

        User user1 = userRepository.getUser(player1).orElseThrow();
        User user2 = userRepository.getUser(player2).orElseThrow();

        int eloDeltaP1 = EloSystem.calculateEloDelta(
                user1.getEloRating(),
                user2.getEloRating(),
                user1.getMatchCount(),
                winnerId == player1,
                isDraw,
                session.getPlayer1Score(),
                session.getPlayer2Score()
        );

        int eloDeltaP2 = EloSystem.calculateEloDelta(
                user2.getEloRating(),
                user1.getEloRating(),
                user2.getMatchCount(),
                winnerId == player2,
                isDraw,
                session.getPlayer2Score(),
                session.getPlayer1Score()
        );

        userRepository.updateEloAndMatchCount(player1, eloDeltaP1);
        userRepository.updateEloAndMatchCount(player2, eloDeltaP2);

        Match match = new Match(
                session.getMatchId(),
                player1,
                player2,
                session.getPlayer1Score(),
                session.getPlayer2Score(),
                isDraw ? null : winnerId,
                MatchStatus.COMPLETED
        );
        matchRepository.save(match);

        List<Message> responses = new ArrayList<>();
        responses.add(buildMessage(player1, Commands.MATCH_ENDED, new MatchEndedResponse(
                isDraw ? null : (winnerId == player1),
                session.getPlayer1Score(),
                session.getPlayer2Score(),
                eloDeltaP1
        )));

        responses.add(buildMessage(player2, Commands.MATCH_ENDED, new MatchEndedResponse(
                isDraw ? null : (winnerId == player2),
                session.getPlayer2Score(),
                session.getPlayer1Score(),
                eloDeltaP2
        )));

        activeGames.remove(session.getMatchId());
        playerToGame.remove(player1);
        playerToGame.remove(player2);

        return responses;
    }

    private List<Message> buildMoveMessages(
            GameSession gameSession, int userId, byte row, byte col
    ) {
        int player1 = gameSession.getPlayer1Id();
        int player2 = gameSession.getPlayer2Id();

        int opponentId = (userId == player1) ? player2 : player1;

        boolean isP1PlayingX = gameSession.isFirstPlayerStartGame();
        boolean isMoveX = (userId == player1) == isP1PlayingX;

        List<Message> responses = new ArrayList<>();
        responses.add(buildMessage(
                userId,
                Commands.PLAYER_MOVE,
                new PlayerMoveResponse(row, col, isMoveX, false)
        ));

        responses.add(buildMessage(
                opponentId,
                Commands.PLAYER_MOVE,
                new PlayerMoveResponse(row, col, isMoveX, true)
        ));
        
        return responses;
    }

    private Message buildMessage(int userId, int commandId, Object jsonData) {
        try {
            String json = mapper.writeValueAsString(jsonData);
            return new Message(
                    (byte) 0,
                    messageId.incrementAndGet(),
                    commandId,
                    userId,
                    json
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize payload", e);
        }
    }
}
