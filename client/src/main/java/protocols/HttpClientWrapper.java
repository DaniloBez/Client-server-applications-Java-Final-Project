package protocols;

import dto.request.BanRequest;
import dto.request.FindUsersRequest;
import dto.request.UserRequest;
import dto.response.AdminUserResponse;
import dto.response.ErrorResponse;
import dto.response.JwtTokenResponse;
import dto.response.LeaderboardEntry;
import dto.response.PageResponse;
import dto.response.UserResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.Getter;
import tools.jackson.core.type.TypeReference;
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
                if (e.getMessage() != null && !e.getMessage().contains("Unrecognized token"))
                    throw new Exception(e.getMessage());

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
                    response.body(),
                    JwtTokenResponse.class
            );
            this.jwtToken = tokenResponse.token();
            return true;
        } else {
            try {
                ErrorResponse err = mapper.readValue(response.body(), ErrorResponse.class);
                throw new Exception(err.errorMessage());
            } catch (Exception e) {
                if (e.getMessage() != null && !e.getMessage().contains("Unrecognized token"))
                    throw new Exception(e.getMessage());

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
        if (response.statusCode() == 200)
            return mapper.readValue(response.body(), UserResponse.class);
        else
            throw new RuntimeException("Failed to fetch user data: " + response.statusCode());
    }

    public void logout() {
        this.jwtToken = null;
    }

    public List<LeaderboardEntry> getLeaderboard() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + serverAddress + ":" + httpPort + "/leaderboard"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200)
            return mapper.readValue(response.body(), new TypeReference<>() {});
        else
            throw new RuntimeException("Failed to fetch leaderboard: " + response.statusCode());
    }

    public PageResponse<AdminUserResponse> getAdminUsers(
            FindUsersRequest searchRequest
    ) throws Exception {
        if (jwtToken == null) throw new Exception("Not logged in");

        String body = "";
        if (searchRequest != null)
            body = mapper.writeValueAsString(searchRequest);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + serverAddress + ":" + httpPort + "/admin/users/search"))
                .header("Authorization", "Bearer " + jwtToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200)
            return mapper.readValue(response.body(), new TypeReference<>() {});
        else
            throw new RuntimeException("Failed to fetch admin connections: "
                    + response.statusCode());
    }

    public void banUser(int userId, boolean isBanned) throws Exception {
        if (jwtToken == null) throw new Exception("Not logged in");

        BanRequest req = new BanRequest(userId, isBanned);
        String body = mapper.writeValueAsString(req);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + serverAddress + ":" + httpPort + "/admin/ban"))
                .header("Authorization", "Bearer " + jwtToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200)
            throw new RuntimeException("Failed to ban user: " + response.statusCode());
    }
}
