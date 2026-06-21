package dto.request;

public record UserFilter(
        String nameLike,
        String userRole,
        Boolean isBanned,
        Integer minElo,
        Integer maxElo,
        Integer minMatchCount,
        Integer maxMatchCount,
        String createdAtFrom,
        String createdAtTo
) {
}
