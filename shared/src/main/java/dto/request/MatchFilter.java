package dto.request;

import java.time.LocalDateTime;

public record MatchFilter(
        Long userId1,
        Long userId2,
        String status,
        LocalDateTime startedAtFrom,
        LocalDateTime startedAtTo,
        LocalDateTime finishedAtFrom,
        LocalDateTime finishedAtTo
) {
}
