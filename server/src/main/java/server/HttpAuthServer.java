package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dto.request.UserRequest;
import dto.response.ErrorResponse;
import dto.response.JwtTokenResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import service.AuthService;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
public class HttpAuthServer {
    private final int port;
    private final AuthService authService;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private HttpServer httpServer;

    public HttpAuthServer(int port, AuthService authService) {
        this.port = port;
        this.authService = authService;
    }

    public void start() {
        try {
            log.info("Starting HTTP Auth Server on port {}", port);
            httpServer = HttpServer.create(new InetSocketAddress(port), 1000);
            httpServer.setExecutor(Executors.newFixedThreadPool(10));
            
            httpServer.createContext("/login", this::loginHandler);
            httpServer.createContext("/register", this::registerHandler);
            
            httpServer.start();
        } catch (IOException e) {
            log.error("Error starting HTTP server on port {}", port, e);
            throw new RuntimeException(e);
        }
    }

    public void stop() {
        log.info("Stopping HTTP Auth Server on port {}", port);
        if (httpServer != null) {
            httpServer.stop(1);
        }
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
                sendError(exchange, 401, "Invalid credentials");
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
        } catch (Exception e) {
            log.error("Error processing login", e);
            sendError(exchange, 400, "Bad Request");
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
            sendError(exchange, 400, "Bad Request");
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
}
