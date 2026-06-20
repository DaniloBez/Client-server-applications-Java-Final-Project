package protocols;

import dto.request.UserRequest;
import dto.response.ErrorResponse;
import dto.response.JwtTokenResponse;
import dto.response.UserResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import lombok.Getter;
import tools.jackson.databind.json.JsonMapper;

public class HttpClientWrapper {
    private final HttpClient client = HttpClient.newHttpClient();
    private final JsonMapper mapper = JsonMapper.builder().build();
    private String serverAddress;
    private int httpPort;
    @Getter
    private String jwtToken;

    public void setConnectionDetails(String serverAddress, int httpPort) {
        this.serverAddress = serverAddress;
        this.httpPort = httpPort;
    }

    public boolean register(String username, String password) throws Exception {
        UserRequest req = new UserRequest(username, password);
        String body = mapper.writeValueAsString(req);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + serverAddress + ":" + httpPort + "/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 201) {
            try {
                ErrorResponse err = mapper.readValue(response.body(), ErrorResponse.class);
                throw new Exception(err.errorMessage());
            } catch (Exception e) {
                if (e.getMessage() != null && !e.getMessage().contains("Unrecognized token")) {
                    throw new Exception(e.getMessage());
                }
                throw new Exception("HTTP " + response.statusCode());
            }
        }
        return true;
    }

    public boolean login(String username, String password) throws Exception {
        UserRequest req = new UserRequest(username, password);
        String body = mapper.writeValueAsString(req);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + serverAddress + ":" + httpPort + "/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(
                request, HttpResponse.BodyHandlers.ofString()
        );
        if (response.statusCode() == 200) {
            JwtTokenResponse tokenResponse = mapper.readValue(
                    response.body(), JwtTokenResponse.class
            );
            this.jwtToken = tokenResponse.token();
            return true;
        } else {
            try {
                ErrorResponse err = mapper.readValue(response.body(), ErrorResponse.class);
                throw new Exception(err.errorMessage());
            } catch (Exception e) {
                if (e.getMessage() != null && !e.getMessage().contains("Unrecognized token")) {
                    throw new Exception(e.getMessage());
                }
                throw new Exception("HTTP " + response.statusCode());
            }
        }
    }

    public UserResponse getUser() throws Exception {
        if (jwtToken == null) {
            throw new Exception("Not logged in");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + serverAddress + ":" + httpPort + "/user"))
                .header("Authorization", "Bearer " + jwtToken)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            return mapper.readValue(response.body(), UserResponse.class);
        } else {
            throw new RuntimeException("Failed to fetch user data: " + response.statusCode());
        }
    }

    public void logout() {
        this.jwtToken = null;
    }
}
