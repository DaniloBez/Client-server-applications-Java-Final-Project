package dto.request;

import java.time.LocalDateTime;

public record UserFilter(
        String nameLike,
        String userRole,
        Boolean isBanned,
        Integer minElo,
        Integer maxElo,
        Integer minMatchCount,
        Integer maxMatchCount,
        LocalDateTime createdAtFrom,
        LocalDateTime createdAtTo
) {
}
