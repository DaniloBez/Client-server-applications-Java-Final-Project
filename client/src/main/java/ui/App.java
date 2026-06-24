package ui;

import decryptor.MessageDecryptor;
import dto.Commands;
import dto.Message;
import dto.request.AuthConnectionRequest;
import dto.response.ErrorResponse;
import dto.response.MatchEndedResponse;
import dto.response.MatchFoundResponse;
import dto.response.PlayerMoveResponse;
import dto.response.RoundEndedResponse;
import dto.response.UserResponse;
import encryptor.MessageEncryptor;
import java.net.InetAddress;
import java.net.URL;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import protocols.ClientTcp;
import protocols.HttpClientWrapper;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
public class App extends Application {
    private StackPane root;
    private HttpClientWrapper httpClient;
    private ClientTcp clientTcp;
    private final JsonMapper mapper = JsonMapper.builder().build();

    private String serverAddress;
    private int tcpPort;
    private GameView currentGameView;
    private UserResponse currentUser;

    @Override
    public void start(Stage primaryStage) {
        this.httpClient = new HttpClientWrapper();
        this.root = new StackPane();

        this.clientTcp = new ClientTcp(
                new MessageEncryptor(),
                new MessageDecryptor(),
                this::handleTcpMessage
        );

        Scene scene = new Scene(root, 600, 450);

        URL cssUrl = getClass().getResource("/styles.css");
        if (cssUrl != null)
            scene.getStylesheets().add(cssUrl.toExternalForm());

        primaryStage.setTitle("Гра Хрестики-нулики");
        primaryStage.setScene(scene);

        showConnectionView();

        primaryStage.setMaximized(true);
        primaryStage.show();
    }

    private void showConnectionView() {
        ConnectionView view = new ConnectionView(details -> {
            this.serverAddress = details.address();
            this.tcpPort = details.tcpPort();
            httpClient.setConnectionDetails(details.address(), details.httpPort());
            showAuthView();
        });
        root.getChildren().setAll(view);
    }

    private void showAuthView() {
        AuthView view = new AuthView(
                httpClient,
                () -> Platform.runLater(this::loadAndShowPlayerMenu),
                this::showConnectionView
        );
        root.getChildren().setAll(view);
    }

    private void loadAndShowPlayerMenu() {
        try {
            currentUser = httpClient.getUser();
            PlayerMenuView view = new PlayerMenuView(
                    currentUser,
                    () -> {
                        log.info("Connecting to game...");
                        startGameConnection();
                    },
                    () -> {
                        httpClient.logout();
                        clientTcp.disconnect();
                        showAuthView();
                    },
                    this::showLeaderboardView,
                    this::showAdminPanelView
            );
            root.getChildren().setAll(view);
        } catch (Exception e) {
            log.warn("Failed to load player menu: {}", e.toString());
            showConnectionView();
        }
    }

    private void showLeaderboardView() {
        LeaderboardView view = new LeaderboardView(httpClient, this::loadAndShowPlayerMenu);
        root.getChildren().setAll(view);
    }

    private void showAdminPanelView() {
        AdminPanelView view = new AdminPanelView(httpClient, this::loadAndShowPlayerMenu);
        root.getChildren().setAll(view);
    }

    private void startGameConnection() {
        try {
            clientTcp.connect(InetAddress.getByName(serverAddress), tcpPort);

            AuthConnectionRequest authentificationRequest = new AuthConnectionRequest(
                    currentUser.id(),
                    httpClient.getJwtToken()
            );
            Message authentificationMessage = new Message(
                    (byte) 1,
                    System.currentTimeMillis(),
                    Commands.AUTH_CONNECTION,
                    currentUser.id(),
                    mapper.writeValueAsString(authentificationRequest)
            );
            clientTcp.sendCommand(authentificationMessage);

            currentGameView = new GameView(clientTcp, currentUser.id(), () -> {
                clientTcp.disconnect();
                Platform.runLater(this::loadAndShowPlayerMenu);
            });
            root.getChildren().setAll(currentGameView);

        } catch (Exception e) {
            log.error("Failed to start game connection", e);
            StyledDialog.show(
                    root,
                    StyledDialog.DialogType.ERROR,
                    "Не вдалося підключитися",
                    e.getMessage() != null ? e.getMessage() : "Невідома помилка"
            );
        }
    }

    private void handleTcpMessage(Message message) {
        Platform.runLater(() -> {
            try {
                if (currentGameView == null
                        && message.getCommandId() != Commands.AUTH_CONNECTION
                        && message.getCommandId() != Commands.BANNED)
                    return;

                switch (message.getCommandId()) {
                    case Commands.AUTH_CONNECTION:
                        if (message.getData().contains("errorType")) {
                            ErrorResponse err = mapper.readValue(
                                    message.getData(),
                                    ErrorResponse.class
                            );
                            showErrorAndLeave("Помилка автентифікації", err.errorMessage());
                        } else {
                            Message joinMsg = new Message(
                                    (byte) 1,
                                    System.currentTimeMillis(),
                                    Commands.JOIN_LOBBY,
                                    currentUser.id(),
                                    ""
                            );
                            clientTcp.sendCommand(joinMsg);
                        }
                        break;
                    case Commands.JOIN_LOBBY:
                    case Commands.LEAVE_LOBBY:
                        if (message.getData().contains("errorType")) {
                            ErrorResponse err = mapper.readValue(
                                    message.getData(),
                                    ErrorResponse.class
                            );
                            showErrorAndLeave("Помилка лобі", err.errorMessage());
                        } else if (message.getCommandId() == Commands.JOIN_LOBBY)
                            currentGameView.setConnectedToLobby();
                        break;
                    case Commands.MATCH_FOUND:
                        MatchFoundResponse matchFound = mapper.readValue(
                                message.getData(),
                                MatchFoundResponse.class
                        );
                        currentGameView.handleMatchFound(matchFound);
                        break;
                    case Commands.PLAYER_MOVE:
                        PlayerMoveResponse move = mapper.readValue(
                                message.getData(),
                                PlayerMoveResponse.class
                        );
                        currentGameView.handlePlayerMove(move);
                        break;
                    case Commands.ROUND_ENDED:
                        RoundEndedResponse round = mapper.readValue(
                                message.getData(),
                                RoundEndedResponse.class
                        );
                        currentGameView.handleRoundEnded(round);
                        break;
                    case Commands.MATCH_ENDED:
                        MatchEndedResponse match = mapper.readValue(
                                message.getData(),
                                MatchEndedResponse.class
                        );
                        currentGameView.handleMatchEnded(match);
                        break;
                    case Commands.BANNED:
                        StyledDialog.show(
                                root,
                                StyledDialog.DialogType.ERROR,
                                "Обліковий запис заблоковано",
                                "Ваш обліковий запис було заблоковано адміністратором.",
                                () -> {
                                    clientTcp.disconnect();
                                    currentUser = null;
                                    showAuthView();
                                }
                        );
                        break;
                    case Commands.INVALID_MOVE:
                        ErrorResponse err = mapper.readValue(
                                message.getData(),
                                ErrorResponse.class
                        );
                        currentGameView.handleError(err.errorMessage());
                        break;
                    default:
                        if (message.getData().contains("errorType")) {
                            ErrorResponse error = mapper.readValue(
                                    message.getData(),
                                    ErrorResponse.class
                            );
                            currentGameView.handleError(error.errorMessage());
                        } else
                            log.warn("Unhandled message command ID: {}", message.getCommandId());
                        break;
                }
            } catch (Exception e) {
                log.error("Failed to parse incoming message", e);
            }
        });
    }

    private void showErrorAndLeave(String title, String content) {
        StyledDialog.show(
                root,
                StyledDialog.DialogType.ERROR,
                title,
                content,
                () -> {
                    clientTcp.disconnect();
                    loadAndShowPlayerMenu();
                }
        );
    }

    public static void main(String[] args) {
        launch(args);
    }
}
