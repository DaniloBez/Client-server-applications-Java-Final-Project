package ui;

import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConnectionView extends VBox {
    public ConnectionView(Consumer<ConnectionDetails> onConnect) {
        setPadding(new Insets(20));
        setSpacing(15);
        setAlignment(Pos.CENTER);
        getStyleClass().add("container");

        Label title = new Label("🌐 Підключення до сервера");
        title.getStyleClass().add("title-label");

        Label subtitle = new Label("Введіть дані для підключення");
        subtitle.setStyle(
                "-fx-text-fill: rgba(255,255,255,0.4); -fx-font-size: 13px;"
        );

        Label addressLabel = new Label("Адреса сервера");
        addressLabel.getStyleClass().add("stat-label");
        TextField addressField = new TextField("127.0.0.1");
        addressField.setPromptText("IP-адреса або домен");
        addressField.setMaxWidth(300);

        VBox addressBox = new VBox(8, addressLabel, addressField);
        addressBox.setAlignment(Pos.CENTER_LEFT);
        addressBox.setMaxWidth(300);

        Label httpPortLabel = new Label("HTTP Порт");
        httpPortLabel.getStyleClass().add("stat-label");
        TextField httpPortField = new TextField("8080");
        httpPortField.setPromptText("HTTP порт");
        httpPortField.setMaxWidth(300);

        VBox httpBox = new VBox(8, httpPortLabel, httpPortField);
        httpBox.setAlignment(Pos.CENTER_LEFT);
        httpBox.setMaxWidth(300);

        Label tcpPortLabel = new Label("TCP Порт");
        tcpPortLabel.getStyleClass().add("stat-label");
        TextField tcpPortField = new TextField("10000");
        tcpPortField.setPromptText("TCP порт");
        tcpPortField.setMaxWidth(300);

        VBox tcpBox = new VBox(8, tcpPortLabel, tcpPortField);
        tcpBox.setAlignment(Pos.CENTER_LEFT);
        tcpBox.setMaxWidth(300);

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("error-label");
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(300);

        Button connectButton = new Button("Підключитися");
        connectButton.getStyleClass().add("primary-button");
        connectButton.setStyle("-fx-min-width: 240;");

        connectButton.setOnAction(_ -> {
            try {
                String address = addressField.getText().trim();
                int httpPort = Integer.parseInt(httpPortField.getText().trim());
                int tcpPort = Integer.parseInt(tcpPortField.getText().trim());

                if (address.isEmpty()) {
                    errorLabel.setText("Адреса не може бути порожньою");
                    return;
                }
                if (httpPort <= 0 || httpPort > 65535
                        || tcpPort <= 0 || tcpPort > 65535) {
                    errorLabel.setText("Порт повинен бути в межах 1 - 65535");
                    return;
                }

                onConnect.accept(new ConnectionDetails(address, httpPort, tcpPort));
            } catch (NumberFormatException ex) {
                log.warn("Invalid port numbers entered", ex);
                errorLabel.setText("Порти повинні бути числами");
            }
        });

        tcpPortField.setOnAction(_ -> connectButton.fire());

        getChildren().addAll(
                title,
                subtitle,
                errorLabel,
                addressBox,
                httpBox,
                tcpBox,
                connectButton
        );
    }

    public record ConnectionDetails(String address, int httpPort, int tcpPort) {
    }
}
