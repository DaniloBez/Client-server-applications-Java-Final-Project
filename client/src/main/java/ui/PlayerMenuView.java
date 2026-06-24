package ui;

import dto.response.UserResponse;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class PlayerMenuView extends VBox {

    public PlayerMenuView(
            UserResponse user,
            Runnable onStartGame,
            Runnable onLogout,
            Runnable onShowLeaderboard,
            Runnable onShowAdminPanel
    ) {
        setPadding(new Insets(20));
        setSpacing(20);
        setAlignment(Pos.CENTER);
        getStyleClass().add("container");

        Label welcomeLabel = new Label("Вітаємо, " + user.username());
        welcomeLabel.getStyleClass().add("title-label");

        VBox statsBox = new VBox(10);
        statsBox.setAlignment(Pos.CENTER);
        statsBox.getStyleClass().add("stats-box");

        Label eloLabel = new Label("Рейтинг Elo: " + user.eloRating());
        eloLabel.getStyleClass().add("stat-label");

        Label matchesLabel = new Label("Зіграно матчів: " + user.matchCount());
        matchesLabel.getStyleClass().add("stat-label");

        String displayRole = "ADMIN".equalsIgnoreCase(user.role())
                ? "Адміністратор" : "Гравець";
        Label roleLabel = new Label("Роль: " + displayRole);
        roleLabel.getStyleClass().add("stat-label");

        statsBox.getChildren().addAll(eloLabel, matchesLabel, roleLabel);

        Button startButton = new Button("▶ Почати гру");
        startButton.getStyleClass().add("primary-button");
        startButton.getStyleClass().add("menu-start-button");
        startButton.setOnAction(_ -> onStartGame.run());

        Button leaderboardButton = new Button("🏆 Рейтинг гравців");
        leaderboardButton.getStyleClass().add("primary-button");
        leaderboardButton.getStyleClass().add("menu-button");
        leaderboardButton.setOnAction(_ -> onShowLeaderboard.run());

        Button adminButton = new Button("Панель Адміністратора");
        adminButton.getStyleClass().add("secondary-button");
        adminButton.getStyleClass().add("menu-admin-button");
        adminButton.setOnAction(_ -> onShowAdminPanel.run());
        adminButton.setVisible("ADMIN".equalsIgnoreCase(user.role()));
        adminButton.setManaged("ADMIN".equalsIgnoreCase(user.role()));

        Button logoutButton = new Button("← Вийти з акаунта");
        logoutButton.getStyleClass().add("secondary-button");
        logoutButton.getStyleClass().add("menu-button");
        logoutButton.setOnAction(_ -> onLogout.run());

        getChildren().addAll(welcomeLabel, statsBox, startButton, leaderboardButton);
        if ("ADMIN".equalsIgnoreCase(user.role()))
            getChildren().add(adminButton);
        getChildren().add(logoutButton);
    }
}
