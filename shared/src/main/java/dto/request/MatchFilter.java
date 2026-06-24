package dto.request;

import java.time.Instant;

public record MatchFilter(
        Integer userId1,
        Integer userId2,
        String status,
        Instant startedAtFrom,
        Instant startedAtTo,
        Instant finishedAtFrom,
        Instant finishedAtTo
) {
}
