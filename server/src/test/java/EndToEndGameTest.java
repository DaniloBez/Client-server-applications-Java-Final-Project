import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import decryptor.MessageDecryptor;
import dto.Commands;
import dto.Message;
import dto.request.AuthConnectionRequest;
import dto.request.PlayerMoveRequest;
import dto.request.UserRequest;
import dto.response.JwtTokenResponse;
import dto.response.MatchEndedResponse;
import dto.response.MatchFoundResponse;
import dto.response.RoundEndedResponse;
import encryptor.MessageEncryptor;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import protocols.ClientTcp;
import repository.MatchRepository;
import repository.UserRepository;
import server.Server;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import utils.DbConnectionPool;

@Testcontainers
public class EndToEndGameTest {

    @Container
    @SuppressWarnings("resource")
    public static PostgreSQLContainer<?> postgresContainer =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("testdb")
                    .withUsername("test")
                    .withPassword("test");

    private static Server server;
    private static DbConnectionPool dbConnectionPool;

    private static final JsonMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @BeforeAll
    public static void setUp() throws Exception {
        Flyway flyway = Flyway.configure().dataSource(
                        postgresContainer.getJdbcUrl(),
                        postgresContainer.getUsername(),
                        postgresContainer.getPassword()
                )
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        dbConnectionPool = new DbConnectionPool(
                10,
                postgresContainer.getJdbcUrl() + "?stringtype=unspecified",
                postgresContainer.getUsername(),
                postgresContainer.getPassword()
        );

        UserRepository userRepository = new UserRepository(dbConnectionPool);
        MatchRepository matchRepository = new MatchRepository(dbConnectionPool);

        server = new Server(
                5,
                new MessageDecryptor(),
                2,
                new MessageEncryptor(),
                2,
                4,
                8081,
                8080,
                "test-secret",
                userRepository,
                matchRepository
        );

        server.start();
        Thread.sleep(1500);
    }

    @AfterAll
    public static void tearDown() {
        if (server != null)
            server.stop();

        if (dbConnectionPool != null)
            dbConnectionPool.closeAll();
    }

    @Test
    public void testFullGamePipeline() throws Exception {
        LinkedBlockingQueue<Message> player1Messages = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<Message> player2Messages = new LinkedBlockingQueue<>();

        ClientTcp player1Client = new ClientTcp(
                new MessageEncryptor(),
                new MessageDecryptor(),
                player1Messages::offer
        );

        ClientTcp player2Client = new ClientTcp(
                new MessageEncryptor(),
                new MessageDecryptor(),
                player2Messages::offer
        );

        player1Client.connect(InetAddress.getByName("localhost"), 8081);
        player2Client.connect(InetAddress.getByName("localhost"), 8081);

        Thread.sleep(1000);

        String token1 = registerAndLogin("Player1", "pass1");
        String token2 = registerAndLogin("Player2", "pass2");

        String player1AuthPayload = mapper.writeValueAsString(
                new AuthConnectionRequest(1, token1)
        );
        player1Client.sendCommand(
                new Message((byte) 1, 100L, Commands.AUTH_CONNECTION, 1, player1AuthPayload)
        );

        Message player1AuthResponse = expectMessage(player1Messages, Commands.AUTH_CONNECTION);
        assertNotNull(player1AuthResponse, "Player1 should receive auth response");
        int user1 = player1AuthResponse.getUserId();

        String player2AuthPayload = mapper.writeValueAsString(
                new AuthConnectionRequest(2, token2)
        );
        player2Client.sendCommand(
                new Message((byte) 1, 101L, Commands.AUTH_CONNECTION, 2, player2AuthPayload)
        );

        Message player2AuthResponse = expectMessage(player2Messages, Commands.AUTH_CONNECTION);
        assertNotNull(player2AuthResponse, "Player2 should receive auth response");
        int user2 = player2AuthResponse.getUserId();

        player1Client.sendCommand(new Message((byte) 1, 102L, Commands.JOIN_LOBBY, user1, ""));
        player2Client.sendCommand(new Message((byte) 1, 103L, Commands.JOIN_LOBBY, user2, ""));

        Message player1JoinResponse = expectMessage(player1Messages, Commands.JOIN_LOBBY);
        assertNotNull(player1JoinResponse, "Player1 should receive join lobby response");

        Message player2JoinResponse = expectMessage(player2Messages, Commands.JOIN_LOBBY);
        assertNotNull(player2JoinResponse, "Player2 should receive join lobby response");

        Message p1Match = expectMessage(player1Messages, Commands.MATCH_FOUND);
        assertNotNull(p1Match, "Player1 should receive match found");

        Message p2Match = expectMessage(player2Messages, Commands.MATCH_FOUND);
        assertNotNull(p2Match, "Player2 should receive match found");

        MatchFoundResponse player1MatchData = mapper.readValue(
                p1Match.getData(),
                MatchFoundResponse.class
        );

        ClientTcp currentTurnClient;
        ClientTcp nextTurnClient;
        LinkedBlockingQueue<Message> currentQueue;
        LinkedBlockingQueue<Message> nextQueue;
        int currentUserId;
        int nextUserId;

        if (player1MatchData.isYourTurn()) {
            currentTurnClient = player1Client;
            nextTurnClient = player2Client;
            currentQueue = player1Messages;
            nextQueue = player2Messages;
            currentUserId = user1;
            nextUserId = user2;
        } else {
            currentTurnClient = player2Client;
            nextTurnClient = player1Client;
            currentQueue = player2Messages;
            nextQueue = player1Messages;
            currentUserId = user2;
            nextUserId = user1;
        }

        // ROUND 1
        // Winner: currentUserId
        byte[][] round1Moves = new byte[][] {
                {0, 0}, {1, 0},
                {0, 1}, {1, 1},
                {0, 2}
        };
        playRound(
                currentTurnClient,
                nextTurnClient,
                currentQueue,
                nextQueue,
                currentUserId,
                nextUserId,
                round1Moves)
        ;

        verifyRoundEnd(currentQueue, nextQueue, true, 1, 0);

        // Swap roles for ROUND 2
        ClientTcp tempClient = currentTurnClient;
        currentTurnClient = nextTurnClient;
        nextTurnClient = tempClient;

        LinkedBlockingQueue<Message> tempQueue = currentQueue;
        currentQueue = nextQueue;
        nextQueue = tempQueue;

        int tempId = currentUserId;
        currentUserId = nextUserId;
        nextUserId = tempId;

        // ROUND 2
        // Winner: currentUserId
        byte[][] round2Moves = new byte[][]{
                {0, 0}, {1, 0},
                {0, 1}, {1, 1},
                {0, 2}
        };
        playRound(
                currentTurnClient,
                nextTurnClient,
                currentQueue,
                nextQueue,
                currentUserId,
                nextUserId,
                round2Moves
        );

        // Player2 (now current) has 1 point, Player1 has 1 point
        verifyRoundEnd(currentQueue, nextQueue, true, 1, 1);

        // Swap for ROUND 3
        tempClient = currentTurnClient;
        currentTurnClient = nextTurnClient;
        nextTurnClient = tempClient;
        
        tempQueue = currentQueue;
        currentQueue = nextQueue;
        nextQueue = tempQueue;
        
        tempId = currentUserId;
        currentUserId = nextUserId;
        nextUserId = tempId;

        // ROUND 3
        // Winner: currentUserId
        byte[][] round3Moves = new byte[][]{
                {0, 0}, {1, 0},
                {0, 1}, {1, 1},
                {0, 2}
        };
        playRound(
                currentTurnClient,
                nextTurnClient,
                currentQueue,
                nextQueue,
                currentUserId,
                nextUserId,
                round3Moves
        );

        // Player1 has 2 points, Player2 has 1 point
        verifyRoundEnd(currentQueue, nextQueue, true, 2, 1);

        // Swap for ROUND 4
        tempClient = currentTurnClient;
        currentTurnClient = nextTurnClient;
        nextTurnClient = tempClient;
        
        tempQueue = currentQueue;
        currentQueue = nextQueue;
        nextQueue = tempQueue;
        
        tempId = currentUserId;
        currentUserId = nextUserId;
        nextUserId = tempId;

        // ROUND 4
        // Winner: nextUserId
        byte[][] round4Moves = new byte[][]{
                {2, 0}, {0, 0},
                {2, 1}, {0, 1},
                {1, 0}, {0, 2}
        };
        playRound(
                currentTurnClient,
                nextTurnClient,
                currentQueue,
                nextQueue,
                currentUserId,
                nextUserId,
                round4Moves
        );

        Message player2EndMessage = expectMessage(currentQueue, Commands.MATCH_ENDED);
        Message player1EndMessage = expectMessage(nextQueue, Commands.MATCH_ENDED);

        assertNotNull(player2EndMessage, "Player2 should get MATCH_ENDED");
        assertNotNull(player1EndMessage, "Player1 should get MATCH_ENDED");

        MatchEndedResponse player2EndData = mapper.readValue(
                player2EndMessage.getData(),
                MatchEndedResponse.class
        );
        MatchEndedResponse player1EndData = mapper.readValue(
                player1EndMessage.getData(),
                MatchEndedResponse.class
        );

        assertFalse(player2EndData.isYouWinner());
        assertEquals((byte) 1, player2EndData.yourFinalScore());
        assertEquals((byte) 3, player2EndData.opponentFinalScore());

        assertTrue(player1EndData.isYouWinner());
        assertEquals((byte) 3, player1EndData.yourFinalScore());
        assertEquals((byte) 1, player1EndData.opponentFinalScore());

        player1Client.disconnect();
        player2Client.disconnect();
    }

    private void playRound(
            ClientTcp firstClient,
            ClientTcp secondClient,
            LinkedBlockingQueue<Message> firstQueue,
            LinkedBlockingQueue<Message> secondQueue,
            int firstId,
            int secondId,
            byte[][] moves
    ) throws Exception {
        boolean firstTurn = true;
        for (byte[] move : moves) {
            ClientTcp activeClient = firstTurn ? firstClient : secondClient;
            int activeId = firstTurn ? firstId : secondId;

            String payload = mapper.writeValueAsString(new PlayerMoveRequest(move[0], move[1]));
            activeClient.sendCommand(
                    new Message(
                            (byte) 1, 
                            System.currentTimeMillis(), 
                            Commands.PLAYER_MOVE, 
                            activeId, 
                            payload)
            );

            Message message1 = expectMessage(firstQueue, Commands.PLAYER_MOVE);
            Message message2 = expectMessage(secondQueue, Commands.PLAYER_MOVE);

            assertNotNull(message1, "Move response missing for first player");
            assertNotNull(message2, "Move response missing for second player");

            firstTurn = !firstTurn;
        }
    }

    private void verifyRoundEnd(
            LinkedBlockingQueue<Message> firstQueue,
            LinkedBlockingQueue<Message> secondQueue,
            boolean firstWon,
            int firstExpectedScore,
            int secondExpectedScore
    ) throws Exception {
        Message message1 = expectMessage(firstQueue, Commands.ROUND_ENDED);
        Message message2 = expectMessage(secondQueue, Commands.ROUND_ENDED);

        assertNotNull(message1, "ROUND_ENDED missing for first player");
        assertNotNull(message2, "ROUND_ENDED missing for second player");

        RoundEndedResponse response1 = mapper.readValue(
                message1.getData(), 
                RoundEndedResponse.class
        );
        RoundEndedResponse response2 = mapper.readValue(
                message2.getData(), 
                RoundEndedResponse.class
        );

        assertEquals(firstWon, response1.isYouWinner());
        assertEquals(!firstWon, response2.isYouWinner());

        assertEquals((byte) firstExpectedScore, response1.yourScore());
        assertEquals((byte) secondExpectedScore, response1.opponentScore());

        assertEquals((byte) secondExpectedScore, response2.yourScore());
        assertEquals((byte) firstExpectedScore, response2.opponentScore());
    }

    private Message expectMessage(
            LinkedBlockingQueue<Message> queue,
            int expectedCommandId
    ) throws Exception {
        long timeout = System.currentTimeMillis() + 10000;
        List<Message> skipped = new ArrayList<>();

        while (System.currentTimeMillis() < timeout) {
            Message message = queue.poll(200, TimeUnit.MILLISECONDS);
            if (message != null) {
                if (message.getCommandId() == expectedCommandId) {
                    for (int i = skipped.size() - 1; i >= 0; i--)
                        queue.offer(skipped.get(i));

                    return message;
                }
                skipped.add(message);
            }
        }
        fail("Did not receive command ID: " + expectedCommandId);
        return null;
    }

    @Test
    public void testTechnicalDefeatByDisconnect() throws Exception {
        LinkedBlockingQueue<Message> player1Messages = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<Message> player2Messages = new LinkedBlockingQueue<>();

        ClientTcp player1Client = new ClientTcp(
                new MessageEncryptor(),
                new MessageDecryptor(),
                player1Messages::offer
        );

        ClientTcp player2Client = new ClientTcp(
                new MessageEncryptor(),
                new MessageDecryptor(),
                player2Messages::offer
        );

        player1Client.connect(InetAddress.getByName("localhost"), 8081);
        player2Client.connect(InetAddress.getByName("localhost"), 8081);
        Thread.sleep(1000);

        String token1 = registerAndLogin("TechPlayer1", "pass");
        String token2 = registerAndLogin("TechPlayer2", "pass");

        player1Client.sendCommand(new Message(
                (byte) 1,
                200L,
                Commands.AUTH_CONNECTION,
                1,
                mapper.writeValueAsString(new AuthConnectionRequest(1, token1))
        ));
        Message player1AuthResponse = expectMessage(player1Messages, Commands.AUTH_CONNECTION);
        assertNotNull(player1AuthResponse);
        int user1 = player1AuthResponse.getUserId();

        player2Client.sendCommand(new Message(
                (byte) 1,
                201L,
                Commands.AUTH_CONNECTION,
                2,
                mapper.writeValueAsString(new AuthConnectionRequest(2, token2))
        ));
        Message player2AuthResponse = expectMessage(player2Messages, Commands.AUTH_CONNECTION);
        assertNotNull(player2AuthResponse);
        int user2 = player2AuthResponse.getUserId();

        player1Client.sendCommand(new Message((byte) 1, 202L, Commands.JOIN_LOBBY, user1, ""));
        player2Client.sendCommand(new Message((byte) 1, 203L, Commands.JOIN_LOBBY, user2, ""));

        expectMessage(player1Messages, Commands.JOIN_LOBBY);
        expectMessage(player2Messages, Commands.JOIN_LOBBY);

        expectMessage(player1Messages, Commands.MATCH_FOUND);
        expectMessage(player2Messages, Commands.MATCH_FOUND);

        player1Client.disconnect();

        Message p2EndMessage = expectMessage(player2Messages, Commands.MATCH_ENDED);
        assertNotNull(p2EndMessage, "P2 should receive MATCH_ENDED after opponent disconnects");

        MatchEndedResponse p2EndData = mapper.readValue(
                p2EndMessage.getData(), MatchEndedResponse.class
        );
        assertTrue(p2EndData.isYouWinner(), "Player 2 should win by technical defeat");

        player2Client.disconnect();
    }

    private String registerAndLogin(String username, String password) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            UserRequest req = new UserRequest(username, password);
            String body = mapper.writeValueAsString(req);

            HttpRequest registerRequest = HttpRequest.newBuilder()
                    .uri(new URI("http://localhost:8080/register"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            client.send(registerRequest, HttpResponse.BodyHandlers.ofString());

            HttpRequest loginRequest = HttpRequest.newBuilder()
                    .uri(new URI("http://localhost:8080/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> loginResp = client.send(
                    loginRequest, HttpResponse.BodyHandlers.ofString()
            );

            JwtTokenResponse tokenResp = mapper.readValue(loginResp.body(), JwtTokenResponse.class);
            return tokenResp.token();

        }
    }
}
