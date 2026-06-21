package dto.request;

public record BanRequest(
        int userId,
        boolean isBanned
) {}
