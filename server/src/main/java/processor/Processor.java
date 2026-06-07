package processor;

import dto.Message;
import dto.response.ErrorResponse;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

public class Processor implements ProcessorInterface {
    private final HashMap<Integer, Function<Message, List<Message>>> router;
    private final JsonMapper mapper;

    public Processor() {
        mapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        router = new HashMap<>();

        initRoutes();
    }

    private void initRoutes() {
    }

    @Override
    public List<Message> process(Message message) {
        try {
            Function<Message, List<Message>> handler = router.get(message.getCommandId());

            if (handler == null)
                return List.of(buildErrorMessage(
                        message,
                        404,
                        "Route Not Found",
                        "Unknown command ID: " + message.getCommandId()
                ));

            return handler.apply(message);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return List.of(buildErrorMessage(message, 400, "Bad Request", e.getMessage()));
        } catch (Exception e) {
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
