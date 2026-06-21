package service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dto.response.LeaderboardEntry;
import entity.User;
import entity.UserRole;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;
import repository.UserRepository;

public class AuthServiceTest {
    private UserRepository userRepository;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        String jwtSecret = "test-secret";
        authService = new AuthService(userRepository, jwtSecret);
    }

    @Test
    void shouldThrowExceptionWhenLoginBannedUser() {
        String username = "bannedUser";
        String password = "password";
        String hash = BCrypt.hashpw(password, BCrypt.gensalt());

        User bannedUser = new User(
                1,
                username,
                hash,
                UserRole.PLAYER,
                0,
                1000,
                true,
                LocalDateTime.now()
        );

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(bannedUser));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> authService.login(username, password)
        );

        assertEquals("Ваш обліковий запис заблоковано", exception.getMessage());
    }

    @Test
    void shouldReturnTokenForValidUnbannedUser() {
        String username = "normalUser";
        String password = "password";
        String hash = BCrypt.hashpw(password, BCrypt.gensalt());

        User user = new User(
                1,
                username,
                hash,
                UserRole.PLAYER,
                0,
                1000,
                false,
                LocalDateTime.now()
        );

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        String token = authService.login(username, password);

        assertNotNull(token);
    }

    @Test
    void shouldCreateAdminSuccessfully() {
        String username = "adminUser";
        String password = "adminPassword";

        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());
        when(userRepository.createAdmin(anyString(), anyString())).thenReturn(10);

        authService.createAdmin(username, password);

        verify(userRepository).createAdmin(anyString(), anyString());
    }

    @Test
    void shouldThrowExceptionWhenCreateAdminWithExistingUsername() {
        String username = "existingAdmin";
        String password = "adminPassword";

        User existingUser = new User(
                1,
                username,
                "hash",
                UserRole.ADMIN,
                0,
                1000,
                false,
                LocalDateTime.now()
        );

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(existingUser));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.createAdmin(username, password)
        );

        assertTrue(exception.getMessage().contains("Користувач з таким іменем вже існує"));
    }

    @Test
    void shouldGetTopPlayers() {
        List<LeaderboardEntry> expectedList = List.of(
                new LeaderboardEntry(1, "p1", 1500, 10),
                new LeaderboardEntry(2, "p2", 1400, 5)
        );

        when(userRepository.getTopPlayers(2)).thenReturn(expectedList);

        List<LeaderboardEntry> result = authService.getTopPlayers(2);

        assertEquals(2, result.size());
        assertEquals(expectedList, result);
        verify(userRepository).getTopPlayers(2);
    }

    @Test
    void shouldSetBannedStatus() {
        long userId = 5L;
        boolean status = true;

        authService.setBannedStatus(userId, status);

        verify(userRepository).setBannedStatus(userId, status);
    }
}
