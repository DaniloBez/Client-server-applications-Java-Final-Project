package entity;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@RequiredArgsConstructor
@AllArgsConstructor
@ToString
public class Match {
    private final long id;
    private final int user1Id;
    private final int user2Id;
    private int user1Score = 0;
    private int user2Score = 0;
    private Integer winnerId = null;
    private MatchStatus status = MatchStatus.IN_PROGRESS;
    private Instant startedAt = Instant.now();
    private Instant finishedAt;

    public Match(
            long id,
            int user1Id,
            int user2Id,
            int user1Score,
            int user2Score,
            Integer winnerId,
            MatchStatus status
    ) {
        this.id = id;
        this.user1Id = user1Id;
        this.user2Id = user2Id;
        this.user1Score = user1Score;
        this.user2Score = user2Score;
        this.winnerId = winnerId;
        this.status = status;
    }
}
