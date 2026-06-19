package ui;

import dto.response.UserResponse;
import java.net.URL;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import protocols.HttpClientWrapper;

@Slf4j
public class App extends Application {
    private Stage window;
    private Scene scene;
    private StackPane root;
    private HttpClientWrapper httpClient;

    @Override
    public void start(Stage primaryStage) {
        this.window = primaryStage;
        this.httpClient = new HttpClientWrapper();
        this.root = new StackPane();

        scene = new Scene(root, 600, 450);

        URL cssUrl = getClass().getResource("/styles.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }

        window.setTitle("Гра Хрестики-нулики");
        window.setScene(scene);

        showConnectionView();

        window.setMaximized(true);
        window.show();
    }

    private void showConnectionView() {
        ConnectionView view = new ConnectionView(details -> {
            httpClient.setConnectionDetails(details.address(), details.httpPort());
            // TODO: TCP
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
            UserResponse user = httpClient.getUser();
            PlayerMenuView view = new PlayerMenuView(user, () -> {
                log.info("Connecting to game...");
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Початок гри");
                alert.setHeaderText(null);
                alert.setContentText("З'єднання з грою...");
                alert.showAndWait();
            }, () -> {
                httpClient.logout();
                showAuthView();
            });
            root.getChildren().setAll(view);
        } catch (Exception e) {
            log.warn("Failed to load player menu: {}", e.toString());
            showConnectionView();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
