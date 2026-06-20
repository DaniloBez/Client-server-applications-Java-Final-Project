package service;

import dto.Commands;
import dto.Message;
import dto.response.ErrorResponse;
import dto.response.SuccessResponse;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import service.game.GameManager;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
public class LobbyService {
    private final ConcurrentLinkedDeque<Integer> waitingQueue = new ConcurrentLinkedDeque<>();
    private final Set<Integer> inQueueSet = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private final JsonMapper mapper = new JsonMapper();
    private final AtomicLong messageId = new AtomicLong(0);

    @Setter
    private Consumer<Message> messageDispatcher;

    private final GameManager gameManager;

    public LobbyService(GameManager gameManager) {
        this.gameManager = gameManager;

        scheduler.scheduleAtFixedRate(
                this::tryMatchPlayers,
                100,
                100,
                TimeUnit.MILLISECONDS
        );
    }

    public List<Message> joinQueue(int playerId) {
        if (inQueueSet.add(playerId)) {
            waitingQueue.addLast(playerId);
            log.info("Player {} joined lobby", playerId);
            return List.of(buildMessage(
                    playerId,
                    Commands.JOIN_LOBBY,
                    new SuccessResponse("Приєднано до лобі", "Ви тепер у черзі на гру.")
            ));
        }
        return List.of(buildMessage(
                playerId,
                Commands.JOIN_LOBBY,
                new ErrorResponse("Вже в черзі", "Ви вже шукаєте гру.")
        ));
    }

    public List<Message> leaveQueue(int playerId) {
        if (inQueueSet.remove(playerId)) {
            waitingQueue.remove(playerId);
            log.info("Player {} left lobby", playerId);
            return List.of(buildMessage(
                    playerId,
                    Commands.LEAVE_LOBBY,
                    new SuccessResponse("Покинуто лобі", "Ви вийшли з черги на гру.")
            ));
        }
        return List.of(buildMessage(
                playerId,
                Commands.LEAVE_LOBBY,
                new ErrorResponse("Не в черзі", "Ви не знаходитесь у черзі на гру.")
        ));
    }

    private void tryMatchPlayers() {
        try {
            while (true) {
                Integer player1 = waitingQueue.pollFirst();
                if (player1 == null) return;

                if (!inQueueSet.remove(player1))
                    continue;

                Integer player2 = waitingQueue.pollFirst();
                if (player2 == null) {
                    inQueueSet.add(player1);
                    waitingQueue.addFirst(player1);
                    return;
                }

                if (!inQueueSet.remove(player2)) {
                    inQueueSet.add(player1);
                    waitingQueue.addFirst(player1);
                    continue;
                }

                log.info("Match found for players {} and {}", player1, player2);
                List<Message> matchMessages = gameManager.createGame(player1, player2);
                if (messageDispatcher != null) {
                    for (Message message : matchMessages)
                        messageDispatcher.accept(message);
                }
            }
        } catch (Exception e) {
            log.error("Error in tryMatchPlayers", e);
        }
    }

    public void stop() {
        log.info("Stopping LobbyService scheduler");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS))
                scheduler.shutdownNow();

        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
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