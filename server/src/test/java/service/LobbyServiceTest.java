package service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import dto.Commands;
import dto.Message;
import dto.response.ErrorResponse;
import dto.response.SuccessResponse;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import service.game.GameManager;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
public class LobbyServiceTest {

    @Mock
    private GameManager mockGameManager;

    @InjectMocks
    private LobbyService lobbyService;
    
    private final JsonMapper mapper = new JsonMapper();

    @AfterEach
    public void tearDown() {
        lobbyService.stop();
    }

    @Test
    public void testJoinQueueSuccess() {
        List<Message> response = lobbyService.joinQueue(1);

        assertEquals(1, response.size());
        Message message = response.getFirst();
        assertEquals(Commands.JOIN_LOBBY, message.getCommandId());
        assertEquals(1, message.getUserId());

        SuccessResponse success = mapper.readValue(message.getData(), SuccessResponse.class);
        assertEquals("Joined lobby", success.title());
    }

    @Test
    public void testJoinQueueAlreadyInQueue() {
        lobbyService.joinQueue(1);
        List<Message> response = lobbyService.joinQueue(1);

        assertEquals(1, response.size());
        Message message = response.getFirst();
        assertEquals(Commands.JOIN_LOBBY, message.getCommandId());
        
        ErrorResponse error = mapper.readValue(message.getData(), ErrorResponse.class);
        assertEquals("Already in queue", error.errorType());
    }

    @Test
    public void testLeaveQueueSuccess() {
        lobbyService.joinQueue(1);
        List<Message> response = lobbyService.leaveQueue(1);

        assertEquals(1, response.size());
        Message message = response.getFirst();
        assertEquals(Commands.LEAVE_LOBBY, message.getCommandId());

        SuccessResponse success = mapper.readValue(message.getData(), SuccessResponse.class);
        assertEquals("Left lobby", success.title());
    }

    @Test
    public void testLeaveQueueNotInQueue() {
        List<Message> response = lobbyService.leaveQueue(1);

        assertEquals(1, response.size());
        Message message = response.getFirst();
        assertEquals(Commands.LEAVE_LOBBY, message.getCommandId());

        ErrorResponse error = mapper.readValue(message.getData(), ErrorResponse.class);
        assertEquals("Not in queue", error.errorType());
    }

    @Test
    public void testMatchPlayersCreatesGame() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Message> dispatchedMessage = new AtomicReference<>();

        lobbyService.setMessageDispatcher(message -> {
            dispatchedMessage.set(message);
            latch.countDown();
        });

        Message matchMessage = new Message((byte) 0, 1, Commands.MATCH_FOUND, 1, "{}");
        lenient().when(mockGameManager.createGame(1, 2)).thenReturn(List.of(matchMessage));
        lenient().when(mockGameManager.createGame(2, 1)).thenReturn(List.of(matchMessage)); 

        lobbyService.joinQueue(1);
        lobbyService.joinQueue(2);

        boolean matched = latch.await(2, TimeUnit.SECONDS);

        assertTrue(matched, "Players should have been matched");
        verify(mockGameManager, atLeastOnce()).createGame(anyInt(), anyInt());
        assertEquals(Commands.MATCH_FOUND, dispatchedMessage.get().getCommandId());
    }
}
