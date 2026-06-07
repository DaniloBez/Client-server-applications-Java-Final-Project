package dto.response;

public record ErrorResponse(
        String errorType,
        String errorMessage
) {
}
