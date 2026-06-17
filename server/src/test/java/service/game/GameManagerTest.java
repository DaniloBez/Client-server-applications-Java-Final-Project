package service.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dto.Commands;
import dto.Message;
import dto.response.ErrorResponse;
import dto.response.MatchEndedResponse;
import dto.response.MatchFoundResponse;
import dto.response.PlayerMoveResponse;
import dto.response.RoundEndedResponse;
import entity.User;
import entity.UserRole;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import repository.MatchRepository;
import repository.UserRepository;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
public class GameManagerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private GameSession mockSession;

    @InjectMocks
    private GameManager gameManager;

    private JsonMapper mapper;

    @BeforeEach
    public void setUp() {
        mapper = new JsonMapper();
    }

    @AfterEach
    public void tearDown() {
        gameManager.stop();
    }

    @Test
    public void testCreateGame() throws Exception {
        User user1 = new User(
                1, 
                "Player1", 
                "password", 
                UserRole.PLAYER, 
                10, 
                1000, 
                false, 
                null
        );
        User user2 = new User(
                2, 
                "Player2",
                "password", 
                UserRole.PLAYER, 
                20, 
                1200, 
                false, 
                null
        );

        when(matchRepository.create(1, 2)).thenReturn(100L);
        when(userRepository.getUser(1)).thenReturn(Optional.of(user1));
        when(userRepository.getUser(2)).thenReturn(Optional.of(user2));

        List<Message> responses = gameManager.createGame(1, 2);

        assertEquals(2, responses.size());

        Message message1 = responses.getFirst();
        assertEquals(Commands.MATCH_FOUND, message1.getCommandId());
        assertEquals(1, message1.getUserId());
        MatchFoundResponse matchResp1 = mapper.readValue(
                message1.getData(), 
                MatchFoundResponse.class
        );
        assertEquals("Player2", matchResp1.opponentName());

        Message message2 = responses.get(1);
        assertEquals(Commands.MATCH_FOUND, message2.getCommandId());
        assertEquals(2, message2.getUserId());
        MatchFoundResponse matchResp2 = mapper.readValue(
                message2.getData(), 
                MatchFoundResponse.class
        );
        assertEquals("Player1", matchResp2.opponentName());
    }

    @Test
    public void testHandleMoveInvalidNotInGame() {
        assertThrows(
                IllegalArgumentException.class,
                () -> gameManager.handleMove(1, (byte) 0, (byte) 0)
        );
    }

    @Test
    public void testHandleMoveInvalid() throws Exception {
        injectMockSession(1, 2, 100L);
        when(mockSession.processMove(1, (byte) 0, (byte) 0)).thenReturn(MoveResult.INVALID);

        List<Message> responses = gameManager.handleMove(1, (byte) 0, (byte) 0);

        assertEquals(1, responses.size());
        Message message = responses.getFirst();
        assertEquals(Commands.INVALID_MOVE, message.getCommandId());
        assertEquals(1, message.getUserId());

        ErrorResponse error = mapper.readValue(message.getData(), ErrorResponse.class);
        assertEquals("Invalid move!", error.errorType());
    }

    @Test
    public void testHandleMoveSuccess() throws Exception {
        injectMockSession(1, 2, 100L);
        when(mockSession.processMove(1, (byte) 0, (byte) 0)).thenReturn(MoveResult.SUCCESS);
        when(mockSession.getPlayer1Id()).thenReturn(1);
        when(mockSession.getPlayer2Id()).thenReturn(2);
        when(mockSession.isFirstPlayerStartGame()).thenReturn(true);

        List<Message> responses = gameManager.handleMove(1, (byte) 0, (byte) 0);

        assertEquals(2, responses.size());
        
        Message message1 = responses.getFirst();
        assertEquals(Commands.PLAYER_MOVE, message1.getCommandId());
        assertEquals(1, message1.getUserId());
        PlayerMoveResponse resp1 = mapper.readValue(message1.getData(), PlayerMoveResponse.class);
        assertTrue(resp1.isX());
        assertFalse(resp1.isYourTurn());

        Message message2 = responses.get(1);
        assertEquals(Commands.PLAYER_MOVE, message2.getCommandId());
        assertEquals(2, message2.getUserId());
        PlayerMoveResponse resp2 = mapper.readValue(message2.getData(), PlayerMoveResponse.class);
        assertTrue(resp2.isX());
        assertTrue(resp2.isYourTurn());
    }

    @Test
    public void testHandleMoveRoundWin() throws Exception {
        injectMockSession(1, 2, 100L);
        when(mockSession.processMove(1, (byte) 0, (byte) 0)).thenReturn(MoveResult.ROUND_WIN);
        when(mockSession.getPlayer1Id()).thenReturn(1);
        when(mockSession.getPlayer2Id()).thenReturn(2);
        when(mockSession.isFirstPlayerStartGame()).thenReturn(true);
        when(mockSession.isFirstPlayerMove()).thenReturn(true);
        when(mockSession.getPlayer1Score()).thenReturn((byte) 1);
        when(mockSession.getPlayer2Score()).thenReturn((byte) 0);

        List<Message> responses = gameManager.handleMove(1, (byte) 0, (byte) 0);

        assertEquals(4, responses.size());
        
        Message message1 = responses.get(2);
        assertEquals(Commands.ROUND_ENDED, message1.getCommandId());
        assertEquals(1, message1.getUserId());
        RoundEndedResponse roundResp1 = mapper.readValue(
                message1.getData(),
                RoundEndedResponse.class
        );
        assertEquals(true, roundResp1.isYouWinner());
        assertEquals((byte) 1, roundResp1.yourScore());

        Message message2 = responses.get(3);
        assertEquals(Commands.ROUND_ENDED, message2.getCommandId());
        assertEquals(2, message2.getUserId());
        RoundEndedResponse roundResp2 = mapper.readValue(
                message2.getData(),
                RoundEndedResponse.class
        );
        assertEquals(false, roundResp2.isYouWinner());
        assertEquals((byte) 0, roundResp2.yourScore());
        
        verify(mockSession).resetBoard();
    }

    @Test
    public void testHandleMoveMatchWin() throws Exception {
        injectMockSession(1, 2, 100L);
        when(mockSession.processMove(1, (byte) 0, (byte) 0)).thenReturn(MoveResult.MATCH_WIN);
        when(mockSession.getPlayer1Id()).thenReturn(1);
        when(mockSession.getPlayer2Id()).thenReturn(2);
        when(mockSession.isFirstPlayerStartGame()).thenReturn(true);
        when(mockSession.getPlayer1Score()).thenReturn((byte) 3);
        when(mockSession.getPlayer2Score()).thenReturn((byte) 0);
        when(mockSession.getMatchId()).thenReturn(100L);

        User user1 = new User(
                1,
                "Player1",
                "password",
                UserRole.PLAYER,
                10,
                1000,
                false,
                null
        );
        User user2 = new User(
                2,
                "Player2",
                "password",
                UserRole.PLAYER,
                10,
                1000,
                false,
                null
        );
        when(userRepository.getUser(1)).thenReturn(Optional.of(user1));
        when(userRepository.getUser(2)).thenReturn(Optional.of(user2));

        List<Message> responses = gameManager.handleMove(1, (byte) 0, (byte) 0);

        assertEquals(4, responses.size());
        
        Message message1 = responses.get(2);
        assertEquals(Commands.MATCH_ENDED, message1.getCommandId());
        assertEquals(1, message1.getUserId());
        MatchEndedResponse matchResp1 = mapper.readValue(
                message1.getData(),
                MatchEndedResponse.class
        );
        assertEquals(true, matchResp1.isYouWinner());

        Message message2 = responses.get(3);
        assertEquals(Commands.MATCH_ENDED, message2.getCommandId());
        assertEquals(2, message2.getUserId());
        MatchEndedResponse matchResp2 = mapper.readValue(
                message2.getData(),
                MatchEndedResponse.class
        );
        assertEquals(false, matchResp2.isYouWinner());
        
        verify(matchRepository).save(any());
        verify(userRepository).updateEloAndMatchCount(eq(1), anyInt());
        verify(userRepository).updateEloAndMatchCount(eq(2), anyInt());
    }

    @Test
    public void testHandleDisconnect() throws Exception {
        injectMockSession(1, 2, 100L);
        when(mockSession.getPlayer1Id()).thenReturn(1);
        when(mockSession.getPlayer2Id()).thenReturn(2);
        when(mockSession.getPlayer1Score()).thenReturn((byte) 1);
        when(mockSession.getPlayer2Score()).thenReturn((byte) 0);

        User user1 = new User(
                1,
                "Player1",
                "password",
                UserRole.PLAYER,
                10,
                1000,
                false,
                null
        );
        User user2 = new User(
                2,
                "Player2",
                "password",
                UserRole.PLAYER,
                10,
                1000,
                false,
                null
        );
        lenient().when(userRepository.getUser(1)).thenReturn(Optional.of(user1));
        lenient().when(userRepository.getUser(2)).thenReturn(Optional.of(user2));

        List<Message> responses = gameManager.handleDisconnect(1);

        assertEquals(1, responses.size()); 
        
        Message message = responses.getFirst();
        assertEquals(Commands.MATCH_ENDED, message.getCommandId());
        assertEquals(2, message.getUserId());
        
        MatchEndedResponse matchResp = mapper.readValue(
                message.getData(),
                MatchEndedResponse.class
        );
        assertEquals(true, matchResp.isYouWinner());
        assertEquals((byte) 3, matchResp.yourFinalScore());
        assertEquals((byte) 0, matchResp.opponentFinalScore());
        
        verify(matchRepository).save(any());
        verify(userRepository).updateEloAndMatchCount(eq(1), anyInt());
        verify(userRepository).updateEloAndMatchCount(eq(2), anyInt());
    }

    @SuppressWarnings("unchecked")
    private void injectMockSession(int player1, int player2, long matchId) throws Exception {
        Field activeGamesField = GameManager.class.getDeclaredField("activeGames");
        activeGamesField.setAccessible(true);
        ConcurrentHashMap<Long, GameSession> activeGames = 
                (ConcurrentHashMap<Long, GameSession>) activeGamesField.get(gameManager);
        activeGames.put(matchId, mockSession);

        Field playerToGameField = GameManager.class.getDeclaredField("playerToGame");
        playerToGameField.setAccessible(true);
        ConcurrentHashMap<Integer, Long> playerToGame = 
                (ConcurrentHashMap<Integer, Long>) playerToGameField.get(gameManager);
        playerToGame.put(player1, matchId);
        playerToGame.put(player2, matchId);
    }
}
