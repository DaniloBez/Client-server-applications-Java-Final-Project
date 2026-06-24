package server;

import com.sun.net.httpserver.HttpExchange;
import dto.request.BanRequest;
import dto.request.FindUsersRequest;
import dto.request.UserRequest;
import dto.response.AdminUserResponse;
import dto.response.ErrorResponse;
import dto.response.JwtTokenResponse;
import dto.response.LeaderboardEntry;
import dto.response.PageResponse;
import dto.response.UserResponse;
import entity.User;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import server.session.ConnectionManager;
import service.UserService;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
public class HttpServer {
    private final int port;
    private final UserService authService;
    private final ConnectionManager connectionManager;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private com.sun.net.httpserver.HttpServer httpServer;

    public HttpServer(int port, UserService authService, ConnectionManager connectionManager) {
        this.port = port;
        this.authService = authService;
        this.connectionManager = connectionManager;
    }

    public void start() {
        try {
            log.info("Starting HTTP Auth Server on port {}", port);
            httpServer = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(port), 1000);
            httpServer.setExecutor(Executors.newFixedThreadPool(10));
            
            httpServer.createContext("/login", this::loginHandler);
            httpServer.createContext("/register", this::registerHandler);
            httpServer.createContext("/user", this::userHandler);
            httpServer.createContext("/leaderboard", this::leaderboardHandler);
            httpServer.createContext("/admin/users/search", this::adminUsersSearchHandler);
            httpServer.createContext("/admin/ban", this::adminBanHandler);
            
            httpServer.start();
        } catch (IOException e) {
            log.error("Error starting HTTP server on port {}", port, e);
            throw new RuntimeException(e);
        }
    }

    public void stop() {
        log.info("Stopping HTTP Auth Server on port {}", port);
        if (httpServer != null)
            httpServer.stop(1);
    }

    private void loginHandler(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        try {
            byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
            String requestBody = new String(bodyBytes, StandardCharsets.UTF_8);
            UserRequest request = mapper.readValue(requestBody, UserRequest.class);

            log.debug("Processing login request for user: {}", request.username());

            String token = authService.login(request.username(), request.password());

            if (token == null) {
                log.warn("Failed login attempt for user: {}", request.username());
                sendError(exchange, 401, "Невірний логін або пароль");
                return;
            }

            log.info("User logged in successfully: {}", request.username());

            JwtTokenResponse response = new JwtTokenResponse(token);
            byte[] responseBytes = mapper.writeValueAsBytes(response);

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        } catch (IllegalStateException e) {
            log.warn("Login blocked: {}", e.getMessage());
            sendError(exchange, 403, e.getMessage());
        } catch (Exception e) {
            log.error("Error processing login", e);
            sendError(exchange, 400, "Невірний запит");
        } finally {
            exchange.close();
        }
    }

    private void registerHandler(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        try {
            byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
            String requestBody = new String(bodyBytes, StandardCharsets.UTF_8);
            UserRequest request = mapper.readValue(requestBody, UserRequest.class);

            log.debug("Processing registration request for user: {}", request.username());

            authService.register(request.username(), request.password());
            log.info("New user registered successfully: {}", request.username());
            
            exchange.sendResponseHeaders(201, -1);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to register user: {}", e.getMessage());
            sendError(exchange, 409, e.getMessage());
        } catch (Exception e) {
            log.error("Error processing registration", e);
            sendError(exchange, 400, "Невірний запит");
        } finally {
            exchange.close();
        }
    }

    private void userHandler(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        try {
            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                sendError(exchange, 401, "Відсутній або недійсний заголовок авторизації");
                return;
            }

            String token = authHeader.substring(7);
            int userId = authService.verify(token);

            if (userId == -1) {
                sendError(exchange, 401, "Недійсний токен");
                return;
            }

            Optional<User> userOptional = authService.getUser(userId);
            if (userOptional.isEmpty()) {
                sendError(exchange, 404, "Користувача не знайдено");
                return;
            }

            User user = userOptional.get();
            UserResponse response = new UserResponse(
                    user.getId(),
                    user.getUsername(),
                    user.getMatchCount(),
                    user.getEloRating(),
                    user.getRole().name()
            );

            byte[] responseBytes = mapper.writeValueAsBytes(response);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        } catch (Exception e) {
            log.error("Error processing user request", e);
            sendError(exchange, 400, "Невірний запит");
        } finally {
            exchange.close();
        }
    }

    private void sendError(HttpExchange exchange, int status, String message) throws IOException {
        byte[] errorBytes = mapper.writeValueAsBytes(new ErrorResponse("Error", message));
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, errorBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(errorBytes);
        }
    }

    private void sendResponse(HttpExchange exchange, int status, Object response)
            throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(response);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
        exchange.close();
    }

    private void leaderboardHandler(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        try {
            List<LeaderboardEntry> topPlayers = authService.getTopPlayers(10);
            byte[] responseBytes = mapper.writeValueAsBytes(topPlayers);

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        } catch (Exception e) {
            log.error("Error processing leaderboard request", e);
            sendError(exchange, 500, "Внутрішня помилка сервера");
        } finally {
            exchange.close();
        }
    }

    private void adminUsersSearchHandler(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Метод не підтримується");
            return;
        }

        try {
            if (!isAdmin(exchange)) return;

            byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
            String requestBody = new String(bodyBytes, StandardCharsets.UTF_8);
            
            // Allow empty body to mean "no filters/pagination"
            FindUsersRequest searchRequest = null;
            if (!requestBody.isBlank()) {
                searchRequest = mapper.readValue(requestBody, FindUsersRequest.class);
            }

            PageResponse<entity.User> usersPage = authService.getUsers(searchRequest);
            java.util.List<Integer> activeUserIds = connectionManager.getActiveUserIds();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                    .withZone(java.time.ZoneId.systemDefault());

            List<AdminUserResponse> adminUserResponses = usersPage.items().stream()
                    .map(user -> new AdminUserResponse(
                            user.getId(),
                            user.getUsername(),
                            activeUserIds.contains(user.getId()) ? "Онлайн" : "Офлайн",
                            user.getMatchCount(),
                            user.getEloRating(),
                            user.getCreatedAt() != null
                                    ? formatter.format(user.getCreatedAt()) : "Н/Д",
                            user.isBanned()
                    ))
                    .collect(java.util.stream.Collectors.toList());

            PageResponse<AdminUserResponse> responsePage = new PageResponse<>(
                    adminUserResponses,
                    usersPage.totalElements(),
                    usersPage.totalPages(),
                    usersPage.currentPage()
            );

            sendResponse(exchange, 200, responsePage);
        } catch (Exception e) {
            log.error("Error fetching admin users: {}", e.getMessage(), e);
            sendError(exchange, 500, "Внутрішня помилка сервера");
        }
    }

    private void adminBanHandler(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        try {
            if (!isAdmin(exchange)) return;

            byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
            String requestBody = new String(bodyBytes, StandardCharsets.UTF_8);
            BanRequest request = mapper.readValue(requestBody, BanRequest.class);

            authService.setBannedStatus(request.userId(), request.isBanned());
            if (request.isBanned()) {
                connectionManager.disconnect(request.userId());
            }

            exchange.sendResponseHeaders(200, -1);
        } catch (Exception e) {
            log.error("Error processing ban request", e);
            sendError(exchange, 500, "Внутрішня помилка сервера");
        } finally {
            exchange.close();
        }
    }

    private boolean isAdmin(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendError(exchange, 401, "Відсутній або недійсний заголовок авторизації");
            return false;
        }

        String token = authHeader.substring(7);
        int userId = authService.verify(token);

        if (userId == -1) {
            sendError(exchange, 401, "Недійсний токен");
            return false;
        }

        Optional<User> userOptional = authService.getUser(userId);
        if (userOptional.isEmpty() || !userOptional.get().getRole().name().equals("ADMIN")) {
            sendError(exchange, 403, "Недостатньо прав");
            return false;
        }

        return true;
    }
}
