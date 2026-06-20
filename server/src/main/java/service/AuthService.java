package service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import entity.User;
import java.util.Date;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;
import repository.UserRepository;

@Slf4j
public class AuthService {
    private final UserRepository userRepository;
    private final Algorithm algorithm;

    public AuthService(UserRepository userRepository, String jwtSecret) {
        this.userRepository = userRepository;
        this.algorithm = Algorithm.HMAC256(jwtSecret);
    }

    public void register(String username, String password) {
        if (username == null || username.isBlank())
            throw new IllegalArgumentException("Ім'я користувача не може бути порожнім");

        if (password == null || password.isBlank())
            throw new IllegalArgumentException("Пароль не може бути порожнім");

        Optional<User> existingUser = userRepository.findByUsername(username);
        if (existingUser.isPresent())
            throw new IllegalArgumentException("Користувач з таким іменем вже існує");

        String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());
        int userId = userRepository.create(username, passwordHash);
        log.info("Registered new user: {} with id {}", username, userId);
    }

    public String login(String username, String password) {
        Optional<User> optionalUser = userRepository.findByUsername(username);
        if (optionalUser.isEmpty())
            return null;

        User user = optionalUser.get();
        if (!BCrypt.checkpw(password, user.getPasswordHash()))
            return null;

        return JWT.create()
                .withIssuer("game-server")
                .withSubject(String.valueOf(user.getId()))
                .withClaim("username", user.getUsername())
                .withExpiresAt(new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000))
                .sign(algorithm);
    }

    public int verify(String token) {
        try {
            JWTVerifier verifier = JWT.require(algorithm)
                    .withIssuer("game-server")
                    .build();
            DecodedJWT jwt = verifier.verify(token);
            return Integer.parseInt(jwt.getSubject());
        } catch (JWTVerificationException exception) {
            log.warn("Invalid JWT token: {}", exception.getMessage());
            return -1;
        } catch (NumberFormatException e) {
            log.warn("Invalid JWT subject format");
            return -1;
        }
    }

    public Optional<User> getUser(int userId) {
        return userRepository.getUser(userId);
    }
}
