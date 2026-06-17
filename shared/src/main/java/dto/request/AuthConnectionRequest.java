package dto.request;

public record AuthConnectionRequest(
        int userId,
        String token
) {
}
