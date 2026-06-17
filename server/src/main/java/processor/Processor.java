package processor;

import dto.Commands;
import dto.Message;
import dto.NetworkMessage;
import dto.request.AuthConnectionRequest;
import dto.request.PlayerMoveRequest;
import dto.response.ErrorResponse;
import dto.response.SuccessResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import server.session.SessionRegistry;
import service.LobbyService;
import service.game.GameManager;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
public class Processor implements ProcessorInterface {
    private final HashMap<Integer, Function<NetworkMessage<Message>, List<Message>>> router;
    private final JsonMapper mapper;
    private final LobbyService lobbyService;
    private final GameManager gameManager;

    public Processor(LobbyService lobbyService, GameManager gameManager) {
        this.lobbyService = lobbyService;
        this.gameManager = gameManager;
        
        mapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        router = new HashMap<>();

        initRoutes();
    }

    private void initRoutes() {
        router.put(Commands.JOIN_LOBBY, this::handleJoinLobby);
        router.put(Commands.LEAVE_LOBBY, this::handleLeaveLobby);
        router.put(Commands.PLAYER_MOVE, this::handlePlayerMove);
        router.put(Commands.PLAYER_DISCONNECTED, this::handlePlayerDisconnected);
        router.put(Commands.AUTH_CONNECTION, this::handleAuthConnection);
    }

    private List<Message> handleAuthConnection(NetworkMessage<Message> netMessage) {
        Message message = netMessage.data();
        try {
            AuthConnectionRequest request = mapper.readValue(
                    message.getData(),
                    AuthConnectionRequest.class
            );
            // TODO: validate JWT token from request.token()
            SessionRegistry.registerUser(request.userId(), netMessage.connectionId());
            
            SuccessResponse response = new SuccessResponse(
                    "Authenticated", 
                    "Successfully authenticated"
            );
            String jsonPayload = mapper.writeValueAsString(response);
            log.info("User {} authenticated successfully", request.userId());
            return List.of(new Message(
                    message.getClientApplicationId(),
                    message.getMessageId(),
                    Commands.AUTH_CONNECTION,
                    request.userId(),
                    jsonPayload
            ));
        } catch (Exception e) {
            log.error("Failed to parse auth connection request", e);
            return List.of(buildErrorMessage(message, 400, "Bad Request", e.getMessage()));
        }
    }

    private List<Message> handleJoinLobby(NetworkMessage<Message> netMessage) {
        return lobbyService.joinQueue(netMessage.data().getUserId());
    }

    private List<Message> handleLeaveLobby(NetworkMessage<Message> netMessage) {
        return lobbyService.leaveQueue(netMessage.data().getUserId());
    }

    private List<Message> handlePlayerMove(NetworkMessage<Message> netMessage) {
        Message message = netMessage.data();
        try {
            PlayerMoveRequest request = mapper.readValue(
                    message.getData(),
                    PlayerMoveRequest.class
            );
            return gameManager.handleMove(message.getUserId(), request.row(), request.col());
        } catch (Exception e) {
            log.error("Failed to parse player move request", e);
            return List.of(buildErrorMessage(message, 400, "Bad Request", e.getMessage()));
        }
    }

    private List<Message> handlePlayerDisconnected(NetworkMessage<Message> netMessage) {
        Message message = netMessage.data();
        List<Message> lobbyMessages = lobbyService.leaveQueue(message.getUserId());
        List<Message> gameMessages = gameManager.handleDisconnect(message.getUserId());

        List<Message> allMessages = new ArrayList<>(lobbyMessages);
        allMessages.addAll(gameMessages);
        return allMessages;
    }

    @Override
    public List<Message> process(NetworkMessage<Message> networkMessage) {
        Message message = networkMessage.data();
        try {
            int commandId = message.getCommandId();
            log.debug("Processing command {} for connection {}", commandId, 
                    networkMessage.connectionId());

            if (commandId != Commands.AUTH_CONNECTION 
                    && commandId != Commands.PLAYER_DISCONNECTED) {
                if (SessionRegistry.getUserId(networkMessage.connectionId()) == null) {
                    return List.of(buildErrorMessage(
                            message,
                            401,
                            "Unauthorized",
                            "You must authenticate first using AUTH_CONNECTION"
                    ));
                }
            }

            Function<NetworkMessage<Message>, List<Message>> handler = router.get(commandId);

            if (handler == null)
                return List.of(buildErrorMessage(
                        message,
                        404,
                        "Route Not Found",
                        "Unknown command ID: " + commandId
                ));

            return handler.apply(networkMessage);
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Bad request from connection {}: {}", networkMessage.connectionId(), 
                    e.getMessage());
            return List.of(buildErrorMessage(message, 400, "Bad Request", e.getMessage()));
        } catch (Exception e) {
            log.error("Unhandled exception processing message for connection " 
                    + networkMessage.connectionId(), e);
            return List.of(buildErrorMessage(
                    message,
                    500,
                    "Internal Server Error",
                    e.getMessage()
            ));
        }
    }

    private Message buildErrorMessage(
            Message originalMessage,
            int errorCommandId,
            String errorType,
            String errorMessageText
    ) {
        try {
            ErrorResponse errorResponse = new ErrorResponse(errorType, errorMessageText);
            String jsonPayload = mapper.writeValueAsString(errorResponse);

            return new Message(
                    originalMessage.getClientApplicationId(),
                    originalMessage.getMessageId(),
                    errorCommandId,
                    originalMessage.getUserId(),
                    jsonPayload
            );
        } catch (Exception jsonEx) {
            return new Message(
                    originalMessage.getClientApplicationId(),
                    originalMessage.getMessageId(),
                    500,
                    originalMessage.getUserId(),
                    "{\"error\": \"Critical serialization error\"}"
            );
        }
    }
}
