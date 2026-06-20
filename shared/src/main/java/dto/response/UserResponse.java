package dto.response;

public record UserResponse(
        int id,
        String username,
        int matchCount,
        int eloRating,
        String role
) {}
