package entity;

import java.time.LocalDateTime;
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
    private final long user1Id;
    private final long user2Id;
    private int user1Score = 0;
    private int user2Score = 0;
    private Long winnerId = null;
    private MatchStatus status = MatchStatus.IN_PROGRESS;
    private LocalDateTime startedAt = LocalDateTime.now();
    private LocalDateTime finishedAt;

    public Match(
            long id,
            long user1Id,
            long user2Id,
            int user1Score,
            int user2Score,
            Long winnerId,
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
