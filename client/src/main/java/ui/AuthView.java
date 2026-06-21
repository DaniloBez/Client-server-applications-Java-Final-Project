package ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import protocols.HttpClientWrapper;

public class AuthView extends StackPane {
    private static final Logger log = LoggerFactory.getLogger(AuthView.class);

    public AuthView(
            HttpClientWrapper httpClient,
            Runnable onAuthSuccess,
            Runnable onBack
    ) {
        setAlignment(Pos.CENTER);

        VBox card = new VBox(16);
        card.setPadding(new Insets(20));
        card.setSpacing(14);
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().add("container");

        Label title = new Label("🔐 Авторизація");
        title.getStyleClass().add("title-label");

        Label userLabel = new Label("Ім'я користувача");
        userLabel.getStyleClass().add("stat-label");
        TextField usernameField = new TextField();
        usernameField.setPromptText("Введіть логін");
        usernameField.setMaxWidth(300);

        VBox userBox = new VBox(8, userLabel, usernameField);
        userBox.setAlignment(Pos.CENTER_LEFT);
        userBox.setMaxWidth(300);

        Label passLabel = new Label("Пароль");
        passLabel.getStyleClass().add("stat-label");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Введіть пароль");
        passwordField.setMaxWidth(300);

        VBox passBox = new VBox(8, passLabel, passwordField);
        passBox.setAlignment(Pos.CENTER_LEFT);
        passBox.setMaxWidth(300);

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("error-label");
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(300);

        Button loginButton = new Button("Увійти");
        loginButton.getStyleClass().add("primary-button");
        loginButton.setStyle("-fx-min-width: 240;");

        Label orLabel = new Label("або");
        orLabel.setStyle("-fx-text-fill: rgba(255,255,255,0.4); -fx-font-size: 12px;");

        Button registerButton = new Button("Створити акаунт");
        registerButton.getStyleClass().add("secondary-button");
        registerButton.setStyle(
                "-fx-min-width: 240;"
                + " -fx-border-color: rgba(108, 92, 231, 0.4);"
                + " -fx-text-fill: #a29bfe;"
        );

        Button backButton = new Button("← Назад");
        backButton.getStyleClass().add("secondary-button");
        backButton.setStyle("-fx-min-width: 140; -fx-pref-width: 140;");
        backButton.setOnAction(e -> onBack.run());

        loginButton.setOnAction(e -> {
            try {
                String user = usernameField.getText().trim();
                String pass = passwordField.getText();
                if (user.isEmpty() || pass.isEmpty()) {
                    errorLabel.getStyleClass().remove("success-label");
                    errorLabel.getStyleClass().add("error-label");
                    errorLabel.setText("Заповніть усі поля");
                    return;
                }

                boolean success = httpClient.login(user, pass);
                if (success)
                    onAuthSuccess.run();
                else {
                    errorLabel.getStyleClass().remove("success-label");
                    errorLabel.getStyleClass().add("error-label");
                    errorLabel.setText("Невірне ім'я або пароль");
                }
            } catch (Exception ex) {
                log.warn("Login failed: {}", ex.toString());
                errorLabel.getStyleClass().remove("success-label");
                errorLabel.getStyleClass().add("error-label");
                String msg = ex.getClass().getSimpleName().equals("ConnectException")
                        ? "Не вдалося підключитися до сервера"
                        : (ex.getMessage() != null
                                ? ex.getMessage() : ex.getClass().getSimpleName());
                errorLabel.setText(msg);
            }
        });

        registerButton.setOnAction(_ -> {
            try {
                String user = usernameField.getText().trim();
                String pass = passwordField.getText();
                if (user.isEmpty() || pass.isEmpty()) {
                    errorLabel.getStyleClass().remove("success-label");
                    errorLabel.getStyleClass().add("error-label");
                    errorLabel.setText("Заповніть усі поля");
                    return;
                }
                boolean success = httpClient.register(user, pass);
                if (success) {
                    errorLabel.getStyleClass().remove("error-label");
                    errorLabel.getStyleClass().add("success-label");
                    StyledDialog.show(this, StyledDialog.DialogType.SUCCESS, "Реєстрація успішна",
                            "✅ Успішно! Тепер ви можете увійти.");
                } else {
                    errorLabel.getStyleClass().remove("success-label");
                    errorLabel.getStyleClass().add("error-label");
                    errorLabel.setText("Помилка реєстрації");
                }
            } catch (Exception ex) {
                log.warn("Registration error: {}", ex.toString());
                errorLabel.getStyleClass().remove("success-label");
                errorLabel.getStyleClass().add("error-label");
                String msg = ex.getClass().getSimpleName().equals("ConnectException")
                        ? "Не вдалося підключитися до сервера"
                        : (ex.getMessage() != null
                                ? ex.getMessage() : ex.getClass().getSimpleName());
                errorLabel.setText(msg);
            }
        });

        passwordField.setOnAction(_ -> loginButton.fire());

        card.getChildren().addAll(
                title,
                errorLabel,
                userBox,
                passBox,
                loginButton,
                orLabel,
                registerButton,
                backButton
        );

        getChildren().add(card);
    }
}
