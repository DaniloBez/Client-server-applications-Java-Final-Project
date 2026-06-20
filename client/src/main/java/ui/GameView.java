package ui;

import dto.Commands;
import dto.Message;
import dto.request.PlayerMoveRequest;
import dto.response.MatchEndedResponse;
import dto.response.MatchFoundResponse;
import dto.response.PlayerMoveResponse;
import dto.response.RoundEndedResponse;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Line;
import javafx.util.Duration;
import lombok.extern.slf4j.Slf4j;
import protocols.ClientTcp;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
public class GameView extends StackPane {
    private final ClientTcp clientTcp;
    private final Runnable onLeave;
    private final int userId;
    private final JsonMapper mapper = JsonMapper.builder().build();

    private final VBox waitingScreen;
    private final VBox gameScreen;

    private final Label statusLabel;
    private final Label scoreLabel;
    private final Label playersLabel;

    private final Button[][] buttons = new Button[3][3];
    private final Line winningLine;

    private boolean isMyTurn = false;
    private boolean isX = false;
    private byte myScore = 0;
    private byte opponentScore = 0;
    private boolean isRoundTransition = false;

    private final Label timerLabel;
    private int timeLeft = 60;
    private Timeline turnTimer;
    private boolean matchEnded = false;
    private boolean isConnectedToLobby = false;

    public GameView(ClientTcp clientTcp, int userId, Runnable onLeave) {
        this.clientTcp = clientTcp;
        this.userId = userId;
        this.onLeave = onLeave;

        getStyleClass().add("container");

        waitingScreen = new VBox(20);
        waitingScreen.setAlignment(Pos.CENTER);
        ProgressIndicator spinner = new ProgressIndicator();
        Label waitingLabel = new Label("Очікування суперника...");
        waitingLabel.getStyleClass().add("title-label");
        Button cancelSearchBtn = new Button("Скасувати пошук");
        cancelSearchBtn.getStyleClass().add("danger-button");
        cancelSearchBtn.setOnAction(_ -> {
            sendLeaveLobby();
            onLeave.run();
        });
        waitingScreen.getChildren().addAll(spinner, waitingLabel, cancelSearchBtn);

        gameScreen = new VBox(20);
        gameScreen.setAlignment(Pos.CENTER);
        gameScreen.setVisible(false);

        playersLabel = new Label();
        playersLabel.getStyleClass().add("stat-label");
        scoreLabel = new Label("0 : 0");
        scoreLabel.getStyleClass().add("title-label");
        statusLabel = new Label();
        statusLabel.getStyleClass().add("stat-label");
        
        timerLabel = new Label("Час: 60");
        timerLabel.getStyleClass().add("stat-label");
        timerLabel.setStyle("-fx-text-fill: #e74c3c;");

        HBox statusBox = new HBox(20, statusLabel, timerLabel);
        statusBox.setAlignment(Pos.CENTER);

        StackPane boardPane = new StackPane();
        GridPane boardGrid = new GridPane();
        boardGrid.setAlignment(Pos.CENTER);
        boardGrid.setHgap(5);
        boardGrid.setVgap(5);

        for (byte r = 0; r < 3; r++) {
            for (byte col = 0; col < 3; col++) {
                Button btn = new Button("");
                btn.setPrefSize(100, 100);
                btn.getStyleClass().add("board-button");
                final byte row = r;
                final byte column = col;
                btn.setOnAction(_ -> handleCellClick(row, column));
                buttons[row][col] = btn;
                boardGrid.add(btn, col, row);
            }
        }

        winningLine = new Line();
        winningLine.setStrokeWidth(8);
        winningLine.setStyle("-fx-stroke: #2ecc71; -fx-stroke-linecap: round;");
        winningLine.setVisible(false);

        Group boardGroup = new Group(boardGrid, winningLine);

        boardPane.getChildren().add(boardGroup);

        gameScreen.getChildren().addAll(playersLabel, scoreLabel, statusBox, boardPane);

        getChildren().addAll(waitingScreen, gameScreen);

        turnTimer = new Timeline(new KeyFrame(Duration.seconds(1), _ -> {
            if (!matchEnded && !waitingScreen.isVisible()) {
                timeLeft--;
                timerLabel.setText("Час: " + timeLeft);
                if (timeLeft <= 0) {
                    timerLabel.setText("Час вичерпано!");
                    turnTimer.stop();
                    
                    if (isMyTurn) {
                        matchEnded = true;
                        Alert alert = new Alert(Alert.AlertType.WARNING);
                        alert.setTitle("Поразка");
                        alert.setHeaderText("Час вичерпано!");
                        alert.setContentText("Ви програли через бездіяльність.");
                        alert.setOnHidden(_ -> onLeave.run());
                        alert.show();
                    }
                }
            }
        }));
        turnTimer.setCycleCount(Animation.INDEFINITE);

        PauseTransition lobbyTimeout = new PauseTransition(Duration.seconds(5));
        lobbyTimeout.setOnFinished(_ -> {
            if (!isConnectedToLobby && waitingScreen.isVisible()) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Помилка");
                alert.setHeaderText("Не вдалося підключитися до сервера");
                alert.setContentText("Сервер не відповідає. Перевірте IP та порт.");
                alert.setOnHidden(ev -> onLeave.run());
                alert.show();
            }
        });
        lobbyTimeout.play();
    }

    public void setConnectedToLobby() {
        this.isConnectedToLobby = true;
    }

    private void handleCellClick(byte row, byte col) {
        if (!isMyTurn || matchEnded || !buttons[row][col].getText().isEmpty())
            return;

        try {
            PlayerMoveRequest request = new PlayerMoveRequest(row, col);
            String json = mapper.writeValueAsString(request);
            Message message = new Message(
                    (byte) 1,
                    System.currentTimeMillis(),
                    Commands.PLAYER_MOVE,
                    userId,
                    json
            );
            clientTcp.sendCommand(message);
        } catch (Exception e) {
            log.error("Failed to send move", e);
        }
    }

    private void sendLeaveLobby() {
        try {
            Message message = new Message(
                    (byte) 1,
                    System.currentTimeMillis(),
                    Commands.LEAVE_LOBBY,
                    userId,
                    ""
            );
            clientTcp.sendCommand(message);
        } catch (Exception e) {
            log.error("Failed to leave lobby", e);
        }
    }

    public void handleMatchFound(MatchFoundResponse response) {
        Platform.runLater(() -> {
            playersLabel.setText(
                    "Ви граєте проти: "
                    + response.opponentName()
                    + " ("
                    + response.opponentElo()
                    + ")"
            );
            isX = response.isYouX();
            isMyTurn = response.isYourTurn();
            myScore = 0;
            opponentScore = 0;
            matchEnded = false;
            isRoundTransition = false;

            updateScoreLabel();
            updateStatusLabel();
            resetBoard();
            
            waitingScreen.setVisible(false);
            gameScreen.setVisible(true);

            resetTimer();
            turnTimer.play();
        });
    }

    public void handlePlayerMove(PlayerMoveResponse response) {
        Platform.runLater(() -> {
            Button button = buttons[response.row()][response.col()];
            String color = response.isX() ? "#e74c3c" : "#3498db";
            button.setText(response.isX() ? "X" : "O");
            button.setStyle("-fx-text-fill: " + color + ";");

            if (!isRoundTransition && !matchEnded) {
                isMyTurn = response.isYourTurn();
                updateStatusLabel();
                resetTimer();
            }

            drawWinningLineIfAny();
        });
    }

    public void handleRoundEnded(RoundEndedResponse response) {
        Platform.runLater(() -> {
            isRoundTransition = true;
            isMyTurn = false;
            isX = response.isYourMove();

            boolean isDraw = response.yourScore() == myScore 
                    && response.opponentScore() == opponentScore;
            statusLabel.setText(Boolean.TRUE.equals(response.isYouWinner()) ? "Раунд виграно!" 
                    : isDraw ? "Нічия у раунді!" : "Раунд програно!");

            myScore = response.yourScore();
            opponentScore = response.opponentScore();
            updateScoreLabel();
            
            drawWinningLineIfAny();

            PauseTransition pause = new PauseTransition(Duration.seconds(2.5));
            pause.setOnFinished(_ -> {
                if (!matchEnded) {
                    resetBoard();
                    isRoundTransition = false;
                    isMyTurn = response.isYourMove();
                    updateStatusLabel();
                    resetTimer();
                }
            });
            pause.play();
        });
    }

    public void handleMatchEnded(MatchEndedResponse response) {
        Platform.runLater(() -> {
            matchEnded = true;
            isMyTurn = false;
            turnTimer.stop();
            
            myScore = response.yourFinalScore();
            opponentScore = response.opponentFinalScore();
            updateScoreLabel();

            drawWinningLineIfAny();

            PauseTransition pause = new PauseTransition(Duration.seconds(2.5));
            pause.setOnFinished(_ -> {
                String result = Boolean.TRUE.equals(response.isYouWinner()) ? "Перемога!" 
                        : Boolean.FALSE.equals(response.isYouWinner()) ? "Поразка!" : "Нічия!";
                String delta = (response.eloDelta() > 0 ? "+" : "") + response.eloDelta();
                
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Матч завершено");
                alert.setHeaderText(result);
                alert.setContentText(String.format("Зміна Elo: %s", delta));
                
                alert.setOnHidden(_ -> onLeave.run());
                alert.show();
            });
            pause.play();
        });
    }

    public void handleError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Помилка");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.show();
    }

    private void updateScoreLabel() {
        scoreLabel.setText(String.format("%d : %d", myScore, opponentScore));
    }

    private void updateStatusLabel() {
        if (!matchEnded) {
            String role = isX ? "X" : "O";
            statusLabel.setText(isMyTurn ? "Ваш хід (Ви " + role + ")" : "Хід суперника...");
            statusLabel.setStyle("");
        }
    }

    private void resetTimer() {
        timeLeft = 60;
        timerLabel.setText("Час: 60");
    }

    private void resetBoard() {
        winningLine.setVisible(false);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                buttons[row][col].setText("");
                buttons[row][col].setStyle("");
            }
        }
    }

    private void drawWinningLineIfAny() {
        String[][] board = new String[3][3];
        for (int row = 0; row < 3; row++) 
            for (int column = 0; column < 3; column++) 
                board[row][column] = buttons[row][column].getText();
        
        for (int row = 0; row < 3; row++) {
            if (!board[row][0].isEmpty() 
                    && board[row][0].equals(board[row][1]) 
                    && board[row][0].equals(board[row][2])
            ) {
                setLineCoords(row, 0, row, 2);
                return;
            }
        }

        for (int column = 0; column < 3; column++) {
            if (!board[0][column].isEmpty() 
                    && board[0][column].equals(board[1][column]) 
                    && board[0][column].equals(board[2][column])
            ) {
                setLineCoords(0, column, 2, column);
                return;
            }
        }

        if (!board[0][0].isEmpty() 
                && board[0][0].equals(board[1][1]) 
                && board[0][0].equals(board[2][2])
        ) {
            setLineCoords(0, 0, 2, 2);
            return;
        }
        if (!board[0][2].isEmpty() 
                && board[0][2].equals(board[1][1]) 
                && board[0][2].equals(board[2][0])
        ) {
            setLineCoords(0, 2, 2, 0);
        }
    }

    private void setLineCoords(int row1, int column1, int row2, int column2) {
        Bounds bound1 = buttons[row1][column1].getBoundsInParent();
        Bounds bound2 = buttons[row2][column2].getBoundsInParent();
        
        winningLine.setStartX(bound1.getMinX() + bound1.getWidth() / 2);
        winningLine.setStartY(bound1.getMinY() + bound1.getHeight() / 2);
        winningLine.setEndX(bound2.getMinX() + bound2.getWidth() / 2);
        winningLine.setEndY(bound2.getMinY() + bound2.getHeight() / 2);
        winningLine.setVisible(true);
        winningLine.toFront();
    }
}
