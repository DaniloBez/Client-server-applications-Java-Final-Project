package dto.request;

import java.time.LocalDateTime;

public record MatchFilter(
        Integer userId1,
        Integer userId2,
        String status,
        LocalDateTime startedAtFrom,
        LocalDateTime startedAtTo,
        LocalDateTime finishedAtFrom,
        LocalDateTime finishedAtTo
) {
}
