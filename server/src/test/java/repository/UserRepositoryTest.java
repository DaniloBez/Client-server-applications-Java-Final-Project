package repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dto.request.FindUsersRequest;
import dto.request.Pagination;
import dto.request.Sorting;
import dto.request.UserFilter;
import dto.response.PageResponse;
import entity.User;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UserRepositoryTest extends BaseRepositoryTest {
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository = new UserRepository(dbConnectionPool);
    }

    @Test
    void shouldCreateAndFindUser() {
        String username = "player1";
        String passwordHash = "hashed_pass";

        long userId = userRepository.create(username, passwordHash);
        Optional<User> foundUser = userRepository.findByUsername(username);

        assertTrue(userId > 0);
        assertTrue(foundUser.isPresent());
        assertEquals(username, foundUser.get().getUsername());
        assertEquals(1000, foundUser.get().getEloRating());
        assertFalse(foundUser.get().isBanned());
    }

    @Test
    void shouldUpdateEloAndMatchCount() {
        long userId = userRepository.create("player2", "pass");

        userRepository.updateEloAndMatchCount(userId, 25);
        User updatedUser = userRepository.findByUsername("player2").orElseThrow();

        assertEquals(1, updatedUser.getMatchCount());
        assertEquals(1025, updatedUser.getEloRating());
    }

    @Test
    void shouldSetBannedStatus() {
        long userId = userRepository.create("hacker", "pass");

        userRepository.setBannedStatus(userId, true);
        User bannedUser = userRepository.findByUsername("hacker").orElseThrow();

        assertTrue(bannedUser.isBanned());
    }

    @Test
    void shouldFindAllUsersWithPaginationAndSorting() {
        userRepository.create("user_a", "pass");
        userRepository.create("user_b", "pass");
        userRepository.create("user_c", "pass");

        FindUsersRequest request = new FindUsersRequest(
                null,
                new Sorting("id", false),
                new Pagination(1, 2)
        );

        PageResponse<User> response = userRepository.findAll(request);

        assertEquals(3, response.totalElements());
        assertEquals(2, response.totalPages());
        assertEquals(2, response.items().size());

        assertEquals("user_c", response.items().get(0).getUsername());
        assertEquals("user_b", response.items().get(1).getUsername());
    }

    @Test
    void shouldReturnEmptyOptionalForUnknownUser() {
        Optional<User> user = userRepository.findByUsername("ghost_user");
        assertTrue(user.isEmpty());
    }

    @Test
    void shouldThrowExceptionWhenUpdatingNonExistentUser() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userRepository.updateEloAndMatchCount(9999L, 25)
        );

        assertTrue(exception.getMessage().contains("was not found"));
    }

    @Test
    void shouldFailWhenCreatingUserWithExistingUsername() {
        userRepository.create("duplicate_user", "pass1");

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> userRepository.create("duplicate_user", "pass2")
        );

        assertTrue(exception.getCause()
                .getMessage()
                .contains("duplicate key value violates unique constraint")
        );
    }

    @Test
    void shouldFilterUsersByRoleAndElo() {
        long noobId = userRepository.create("noob_player", "pass");
        long proId = userRepository.create("pro_player", "pass");

        userRepository.updateEloAndMatchCount(proId, 500);

        UserFilter filter = new UserFilter(
                null, "PLAYER", null, 1200, null, null, null, null, null
        );
        FindUsersRequest request = new FindUsersRequest(filter, null, null);
        PageResponse<User> response = userRepository.findAll(request);

        assertEquals(1, response.totalElements());
        assertEquals("pro_player", response.items().getFirst().getUsername());
    }
}
