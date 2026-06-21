package dto.response;

public record LeaderboardEntry(
        int rank,
        String username,
        int eloRating,
        int matchCount
) {}
