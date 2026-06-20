package ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import protocols.HttpClientWrapper;

public class AuthView extends VBox {
    private static final Logger log = LoggerFactory.getLogger(AuthView.class);

    public AuthView(HttpClientWrapper httpClient, Runnable onAuthSuccess, Runnable onBack) {
        setPadding(new Insets(20));
        setSpacing(15);
        setAlignment(Pos.CENTER);
        getStyleClass().add("container");

        Label title = new Label("Авторизація");
        title.getStyleClass().add("title-label");

        Label userLabel = new Label("Ім'я користувача:");
        userLabel.getStyleClass().add("stat-label");
        TextField usernameField = new TextField();
        usernameField.setPromptText("Ім'я користувача");
        usernameField.setMaxWidth(300);

        Label passLabel = new Label("Пароль:");
        passLabel.getStyleClass().add("stat-label");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Пароль");
        passwordField.setMaxWidth(300);

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("error-label");

        Button loginButton = new Button("Увійти");
        loginButton.getStyleClass().add("primary-button");
        
        Button registerButton = new Button("Реєстрація");
        registerButton.getStyleClass().add("secondary-button");

        Button backButton = new Button("Назад");
        backButton.getStyleClass().add("secondary-button");
        backButton.setOnAction(e -> onBack.run());

        HBox buttonBox = new HBox(10, backButton, loginButton, registerButton);
        buttonBox.setAlignment(Pos.CENTER);

        loginButton.setOnAction(e -> {
            try {
                String user = usernameField.getText().trim();
                String pass = passwordField.getText();
                if (user.isEmpty() || pass.isEmpty()) {
                    errorLabel.setText("Ім'я користувача та пароль не можуть бути порожніми");
                    return;
                }
                boolean success = httpClient.login(user, pass);
                if (success) {
                    onAuthSuccess.run();
                } else {
                    errorLabel.setText("Невірне ім'я користувача або пароль");
                }
            } catch (Exception ex) {
                log.warn("Login failed: {}", ex.toString());
                String msg = ex.getClass().getSimpleName().equals("ConnectException") 
                        ? "Не вдалося підключитися до сервера" 
                        : (ex.getMessage() != null 
                                ? ex.getMessage() : ex.getClass().getSimpleName());
                errorLabel.setText("Помилка входу: " + msg);
            }
        });

        registerButton.setOnAction(e -> {
            try {
                String user = usernameField.getText().trim();
                String pass = passwordField.getText();
                if (user.isEmpty() || pass.isEmpty()) {
                    errorLabel.setText("Ім'я користувача та пароль не можуть бути порожніми");
                    return;
                }
                boolean success = httpClient.register(user, pass);
                if (success) {
                    errorLabel.getStyleClass().remove("error-label");
                    errorLabel.getStyleClass().add("success-label");
                    errorLabel.setText("Успішно зареєстровано. Тепер ви можете увійти.");
                } else {
                    errorLabel.setText("Помилка реєстрації");
                }
            } catch (Exception ex) {
                log.warn("Registration error: {}", ex.toString());
                String msg = ex.getClass().getSimpleName().equals("ConnectException") 
                        ? "Не вдалося підключитися до сервера" 
                        : (ex.getMessage() != null 
                                ? ex.getMessage() : ex.getClass().getSimpleName());
                errorLabel.setText("Помилка реєстрації: " + msg);
            }
        });

        getChildren().addAll(
                title, userLabel, usernameField, passLabel, passwordField, 
                buttonBox, errorLabel
        );
    }
}
