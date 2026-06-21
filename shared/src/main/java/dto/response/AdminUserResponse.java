package dto.response;

public record AdminUserResponse(
        int id,
        String username,
        String status,
        int matchCount,
        int eloRating,
        String createdAt,
        boolean isBanned
) {}
