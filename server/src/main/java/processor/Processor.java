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
import service.UserService;
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
    private final UserService authService;

    public Processor(LobbyService lobbyService, GameManager gameManager, UserService authService) {
        this.lobbyService = lobbyService;
        this.gameManager = gameManager;
        this.authService = authService;
        
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
            
            int userId = authService.verify(request.token());
            if (userId == -1) {
                log.warn(
                        "Invalid JWT token during TCP auth from connection {}",
                        netMessage.connectionId()
                );
                return List.of(buildErrorMessage(
                        message,
                        401,
                        "Неавторизовано",
                        "Недійсний токен"
                ));
            }

            SessionRegistry.registerUser(userId, netMessage.connectionId());
            
            SuccessResponse response = new SuccessResponse(
                    "Автентифіковано", 
                    "Успішна автентифікація"
            );
            String jsonPayload = mapper.writeValueAsString(response);
            log.info("User {} authenticated successfully", request.userId());
            return List.of(new Message(
                    message.getClientApplicationId(),
                    message.getMessageId(),
                    Commands.AUTH_CONNECTION,
                    userId,
                    jsonPayload
            ));
        } catch (Exception e) {
            log.error("Failed to parse auth connection request", e);
            return List.of(buildErrorMessage(message, 400, "Невірний запит", e.getMessage()));
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
            return List.of(buildErrorMessage(message, 400, "Невірний запит", e.getMessage()));
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
                            "Неавторизовано",
                            "Ви повинні спочатку авторизуватись за допомогою AUTH_CONNECTION"
                    ));
                }
            }

            Function<NetworkMessage<Message>, List<Message>> handler = router.get(commandId);

            if (handler == null)
                return List.of(buildErrorMessage(
                        message,
                        404,
                        "Маршрут не знайдено",
                        "Невідомий ID команди: " + commandId
                ));

            return handler.apply(networkMessage);
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Bad request from connection {}: {}", networkMessage.connectionId(), 
                    e.getMessage());
            return List.of(buildErrorMessage(message, 400, "Невірний запит", e.getMessage()));
        } catch (Exception e) {
            log.error(
                    "Unhandled exception processing message for connection {}",
                    networkMessage.connectionId(),
                    e
            );
            return List.of(buildErrorMessage(
                    message,
                    500,
                    "Внутрішня помилка сервера",
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
