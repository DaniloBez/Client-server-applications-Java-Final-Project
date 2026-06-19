package ui;

import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectionView extends VBox {
    private static final Logger log = LoggerFactory.getLogger(ConnectionView.class);

    public ConnectionView(Consumer<ConnectionDetails> onConnect) {
        setPadding(new Insets(20));
        setSpacing(15);
        setAlignment(Pos.CENTER);
        getStyleClass().add("container");

        Label title = new Label("Підключення до сервера");
        title.getStyleClass().add("title-label");

        Label addressLabel = new Label("Адреса сервера:");
        addressLabel.getStyleClass().add("stat-label");
        TextField addressField = new TextField("127.0.0.1");
        addressField.setPromptText("Адреса сервера");
        addressField.setMaxWidth(300);

        Label httpPortLabel = new Label("HTTP Порт:");
        httpPortLabel.getStyleClass().add("stat-label");
        TextField httpPortField = new TextField("8080");
        httpPortField.setPromptText("HTTP Порт");
        httpPortField.setMaxWidth(300);

        Label tcpPortLabel = new Label("TCP Порт:");
        tcpPortLabel.getStyleClass().add("stat-label");
        TextField tcpPortField = new TextField("10000");
        tcpPortField.setPromptText("TCP Порт");
        tcpPortField.setMaxWidth(300);

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("error-label");

        Button connectButton = new Button("Підключитися");
        connectButton.getStyleClass().add("primary-button");
        connectButton.setMaxWidth(300);

        connectButton.setOnAction(e -> {
            try {
                String address = addressField.getText().trim();
                int httpPort = Integer.parseInt(httpPortField.getText().trim());
                int tcpPort = Integer.parseInt(tcpPortField.getText().trim());

                if (address.isEmpty()) {
                    errorLabel.setText("Адреса не може бути порожньою");
                    return;
                }
                if (httpPort <= 0 || httpPort > 65535 || tcpPort <= 0 || tcpPort > 65535) {
                    errorLabel.setText("Порт повинен бути в межах 1 - 65535");
                    return;
                }

                onConnect.accept(new ConnectionDetails(address, httpPort, tcpPort));
            } catch (NumberFormatException ex) {
                log.warn("Invalid port numbers entered", ex);
                errorLabel.setText("Порти повинні бути числами");
            }
        });

        getChildren().addAll(
                title, addressLabel, addressField, httpPortLabel, httpPortField, 
                tcpPortLabel, tcpPortField, connectButton, errorLabel
        );
    }

    public record ConnectionDetails(String address, int httpPort, int tcpPort) {}
}
