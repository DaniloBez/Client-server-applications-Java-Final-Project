package entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@AllArgsConstructor
@ToString
public class User {
    private int id;
    private String username;
    private String passwordHash;
    private UserRole role;
    private int matchCount;
    private int eloRating;
    private boolean isBanned;
    private LocalDateTime createdAt;
}
