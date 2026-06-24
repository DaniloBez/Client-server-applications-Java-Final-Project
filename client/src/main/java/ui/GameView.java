package ui;

import dto.Commands;
import dto.Message;
import dto.request.PlayerMoveRequest;
import dto.response.MatchEndedResponse;
import dto.response.MatchFoundResponse;
import dto.response.PlayerMoveResponse;
import dto.response.RoundEndedResponse;
import java.util.ArrayList;
import java.util.List;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
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
    private final List<Animation> activeAnimations = new ArrayList<>();

    private boolean isMyTurn = false;
    private boolean isX = false;
    private byte myScore = 0;
    private byte opponentScore = 0;
    private boolean isRoundTransition = false;
    private boolean isWinningAnimationPlaying = false;

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
        spinner.setMaxSize(60, 60);
        Label waitingLabel = new Label("Очікування суперника...");
        waitingLabel.getStyleClass().add("title-label");
        Label waitingHint = new Label("Шукаємо гідного противника для вас");
        waitingHint.getStyleClass().add("stat-label");
        Button cancelSearchBtn = new Button("Скасувати пошук");
        cancelSearchBtn.getStyleClass().add("danger-button");
        cancelSearchBtn.setOnAction(_ -> {
            sendLeaveLobby();
            onLeave.run();
        });
        waitingScreen.getChildren().addAll(
                spinner, waitingLabel, waitingHint, cancelSearchBtn
        );

        gameScreen = new VBox(18);
        gameScreen.setAlignment(Pos.CENTER);
        gameScreen.setVisible(false);

        playersLabel = new Label();
        playersLabel.getStyleClass().add("stat-label");
        playersLabel.getStyleClass().add("game-players-label");

        scoreLabel = new Label("0 : 0");
        scoreLabel.getStyleClass().add("game-score-label");

        statusLabel = new Label();
        statusLabel.getStyleClass().add("stat-label");
        statusLabel.getStyleClass().add("game-status-label");

        timerLabel = new Label("⏱ 60");
        timerLabel.getStyleClass().add("game-timer-label");

        HBox statusBox = new HBox(20, statusLabel, timerLabel);
        statusBox.setAlignment(Pos.CENTER);

        GridPane boardGrid = new GridPane();
        boardGrid.setAlignment(Pos.CENTER);
        boardGrid.setHgap(8);
        boardGrid.setVgap(8);

        for (byte r = 0; r < 3; r++) {
            for (byte col = 0; col < 3; col++) {
                Button btn = new Button("");
                btn.setPrefSize(105, 105);
                btn.getStyleClass().add("board-button");
                final byte row = r;
                final byte column = col;
                btn.setOnAction(_ -> handleCellClick(row, column));
                buttons[row][col] = btn;
                boardGrid.add(btn, col, row);
            }
        }

        gameScreen.getChildren().addAll(
                playersLabel, scoreLabel, statusBox, boardGrid
        );

        getChildren().addAll(waitingScreen, gameScreen);

        turnTimer = new Timeline(new KeyFrame(Duration.seconds(1), _ -> {
            if (!matchEnded && !waitingScreen.isVisible()) {
                timeLeft--;
                timerLabel.setText("⏱ " + timeLeft);
                if (timeLeft <= 10) {
                    timerLabel.getStyleClass().removeAll("game-timer-normal");
                    timerLabel.getStyleClass().add("game-timer-warning");
                }
                if (timeLeft <= 0) {
                    timerLabel.setText("⏱ Час вичерпано!");
                    turnTimer.stop();

                    if (isMyTurn) {
                        matchEnded = true;
                        StyledDialog.show(
                                this,
                                StyledDialog.DialogType.WARNING,
                                "Час вичерпано!",
                                "Ви програли через бездіяльність.",
                                onLeave
                        );
                    }
                }
            }
        }));
        turnTimer.setCycleCount(Animation.INDEFINITE);

        PauseTransition lobbyTimeout = new PauseTransition(Duration.seconds(5));
        lobbyTimeout.setOnFinished(_ -> {
            if (!isConnectedToLobby && waitingScreen.isVisible()) {
                StyledDialog.show(
                        this,
                        StyledDialog.DialogType.ERROR,
                        "Не вдалося підключитися",
                        "Сервер не відповідає. Перевірте IP та порт.",
                        onLeave
                );
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
                    "Проти: "
                            + (response.opponentName() != null
                            ? response.opponentName() : "Невідомо")
                            + " (Elo "
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
            boolean isMoveX = response.isX();
            Button button = buttons[response.row()][response.col()];
            button.setText(isMoveX ? "X" : "O");
            button.getStyleClass().removeAll("game-cell-x", "game-cell-o");
            button.getStyleClass().add(isMoveX ? "game-cell-x" : "game-cell-o");

            ScaleTransition pop = new ScaleTransition(
                    Duration.millis(150), button
            );
            pop.setFromX(0.7);
            pop.setFromY(0.7);
            pop.setToX(1.0);
            pop.setToY(1.0);
            pop.play();
            activeAnimations.add(pop);

            if (!isRoundTransition && !matchEnded) {
                isMyTurn = response.isYourTurn();
                updateStatusLabel();
                resetTimer();
            }

            highlightWinningCells();
        });
    }

    public void handleRoundEnded(RoundEndedResponse response) {
        Platform.runLater(() -> {
            isRoundTransition = true;
            isMyTurn = false;
            isX = response.isYourMove();

            boolean isDraw = response.yourScore() == myScore
                    && response.opponentScore() == opponentScore;

            String roundResult;
            if (Boolean.TRUE.equals(response.isYouWinner()))
                roundResult = "🎉 Раунд виграно!";
            else if (isDraw)
                roundResult = "🤝 Нічия у раунді!";
            else
                roundResult = "Раунд програно!";

            statusLabel.setText(roundResult);

            myScore = response.yourScore();
            opponentScore = response.opponentScore();
            updateScoreLabel();

            highlightWinningCells();

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

            highlightWinningCells();

            PauseTransition pause = new PauseTransition(Duration.seconds(2.5));
            pause.setOnFinished(_ -> {
                String result;
                StyledDialog.DialogType dlgType;
                if (Boolean.TRUE.equals(response.isYouWinner())) {
                    result = "🏆 Перемога!";
                    dlgType = StyledDialog.DialogType.SUCCESS;
                } else if (Boolean.FALSE.equals(response.isYouWinner())) {
                    result = "Поразка";
                    dlgType = StyledDialog.DialogType.ERROR;
                } else {
                    result = "🤝 Нічия!";
                    dlgType = StyledDialog.DialogType.INFO;
                }
                String delta = (response.eloDelta() > 0 ? "+" : "")
                        + response.eloDelta();

                StyledDialog.show(
                        this,
                        dlgType,
                        result,
                        "Зміна Elo: " + delta,
                        onLeave
                );
            });
            pause.play();
        });
    }

    public void handleError(String message) {
        Platform.runLater(() ->
                StyledDialog.show(
                        this,
                        StyledDialog.DialogType.ERROR,
                        "Помилка",
                        message
                )
        );
    }

    private void updateScoreLabel() {
        scoreLabel.setText(
                String.format("%d : %d", myScore, opponentScore)
        );
    }

    private void updateStatusLabel() {
        if (!matchEnded) {
            String role = isX ? "X" : "O";
            if (isMyTurn) {
                statusLabel.setText("🟢 Ваш хід (Ви " + role + ")");
                statusLabel.getStyleClass().removeAll(
                        "game-status-lose",
                        "game-status-win",
                        "game-status-label"
                );
                statusLabel.getStyleClass().add("game-status-win");
            } else {
                statusLabel.setText("⏳ Хід суперника...");
                statusLabel.getStyleClass().removeAll(
                        "game-status-lose",
                        "game-status-win",
                        "game-status-label"
                );
                statusLabel.getStyleClass().add("game-status-label");
            }
        }
    }

    private void resetTimer() {
        timeLeft = 60;
        timerLabel.setText("⏱ 60");
        timerLabel.getStyleClass().removeAll("game-timer-warning");
        timerLabel.getStyleClass().add("game-timer-normal");
    }

    private void resetBoard() {
        isWinningAnimationPlaying = false;
        for (Animation anim : activeAnimations) {
            anim.stop();
        }
        activeAnimations.clear();

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                buttons[row][col].setText("");
                buttons[row][col].getStyleClass().removeAll(
                        "game-cell-x",
                        "game-cell-o",
                        "game-cell-win"
                );
                buttons[row][col].setEffect(null);
                buttons[row][col].setOpacity(1.0);
                buttons[row][col].setScaleX(1.0);
                buttons[row][col].setScaleY(1.0);
            }
        }
    }

    private void highlightWinningCells() {
        if (isWinningAnimationPlaying) return;
        
        int[] winCells = findWinningCells();
        if (winCells == null) return;

        isWinningAnimationPlaying = true;

        for (Animation anim : activeAnimations)
            anim.stop();

        activeAnimations.clear();

        DropShadow dropShadow = new DropShadow();
        dropShadow.setColor(Color.web("#51cf66"));
        dropShadow.setRadius(20);
        dropShadow.setSpread(0.4);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                boolean isWin = false;
                for (int i = 0; i < winCells.length; i += 2) {
                    if (winCells[i] == row && winCells[i + 1] == col) {
                        isWin = true;
                        break;
                    }
                }
                if (isWin) {
                    buttons[row][col].setEffect(dropShadow);
                    buttons[row][col].getStyleClass().add("game-cell-win");

                    ScaleTransition pulse = new ScaleTransition(
                            Duration.millis(400), buttons[row][col]
                    );
                    pulse.setFromX(1.0);
                    pulse.setFromY(1.0);
                    pulse.setToX(1.1);
                    pulse.setToY(1.1);
                    pulse.setCycleCount(Animation.INDEFINITE);
                    pulse.setAutoReverse(true);
                    pulse.play();
                    activeAnimations.add(pulse);
                } else {
                    FadeTransition dim = new FadeTransition(
                            Duration.millis(300), buttons[row][col]
                    );
                    dim.setToValue(0.2);
                    dim.play();
                    activeAnimations.add(dim);
                }
            }
        }
    }

    private int[] findWinningCells() {
        String[][] board = new String[3][3];
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 3; c++)
                board[r][c] = buttons[r][c].getText();

        for (int r = 0; r < 3; r++) {
            if (!board[r][0].isEmpty()
                    && board[r][0].equals(board[r][1])
                    && board[r][0].equals(board[r][2])) {
                return new int[]{r, 0, r, 1, r, 2};
            }
        }
        for (int c = 0; c < 3; c++) {
            if (!board[0][c].isEmpty()
                    && board[0][c].equals(board[1][c])
                    && board[0][c].equals(board[2][c])) {
                return new int[]{0, c, 1, c, 2, c};
            }
        }
        if (!board[0][0].isEmpty()
                && board[0][0].equals(board[1][1])
                && board[0][0].equals(board[2][2])) {
            return new int[]{0, 0, 1, 1, 2, 2};
        }
        if (!board[0][2].isEmpty()
                && board[0][2].equals(board[1][1])
                && board[0][2].equals(board[2][0])) {
            return new int[]{0, 2, 1, 1, 2, 0};
        }
        return null;
    }
}
