package ui;

import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class StyledDialog extends StackPane {

    public enum DialogType {
        INFO, SUCCESS, WARNING, ERROR
    }

    private final Runnable onClose;

    public StyledDialog(
            DialogType type,
            String title,
            String message,
            Runnable onClose
    ) {
        this.onClose = onClose;

        setStyle("-fx-background-color: rgba(0,0,0,0.55);");
        setAlignment(Pos.CENTER);

        VBox card = new VBox(18);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(32, 40, 28, 40));
        card.setMaxWidth(420);
        card.setMaxHeight(300);
        card.setStyle(
                "-fx-background-color: rgba(30, 28, 60, 0.95);"
                + " -fx-background-radius: 18;"
                + " -fx-border-radius: 18;"
                + " -fx-border-color: rgba(255,255,255,0.12);"
                + " -fx-border-width: 1;"
                + " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 40, 0, 0, 10);"
        );

        Label icon = new Label(getIcon(type));
        icon.setStyle("-fx-font-size: 42px;");

        Label titleLabel = new Label(title);
        titleLabel.setStyle(
                "-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: white;"
        );
        titleLabel.setWrapText(true);

        Label msgLabel = new Label(message);
        msgLabel.setStyle(
                "-fx-font-size: 14px; -fx-text-fill: #b0b5c0;"
                + " -fx-text-alignment: center;"
        );
        msgLabel.setWrapText(true);
        msgLabel.setMaxWidth(340);

        Button okBtn = new Button("OK");
        okBtn.getStyleClass().add("primary-button");
        okBtn.setStyle(
                okBtn.getStyle()
                + " -fx-min-width: 120; -fx-pref-height: 38;"
        );
        okBtn.setOnAction(_ -> close());

        card.getChildren().addAll(icon, titleLabel, msgLabel, okBtn);
        getChildren().add(card);

        setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), this);
        fadeIn.setToValue(1);
        fadeIn.play();

        ScaleTransition scaleIn = new ScaleTransition(Duration.millis(200), card);
        scaleIn.setFromX(0.85);
        scaleIn.setFromY(0.85);
        scaleIn.setToX(1);
        scaleIn.setToY(1);
        scaleIn.play();
    }

    public StyledDialog(
            DialogType type,
            String title,
            String message,
            String primaryBtnText,
            String secondaryBtnText,
            Runnable onPrimary,
            Runnable onSecondary
    ) {
        this.onClose = onSecondary;

        setStyle("-fx-background-color: rgba(0,0,0,0.55);");
        setAlignment(Pos.CENTER);

        VBox card = new VBox(18);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(32, 40, 28, 40));
        card.setMaxWidth(420);
        card.setMaxHeight(300);
        card.setStyle(
                "-fx-background-color: rgba(30, 28, 60, 0.95);"
                + " -fx-background-radius: 18;"
                + " -fx-border-radius: 18;"
                + " -fx-border-color: rgba(255,255,255,0.12);"
                + " -fx-border-width: 1;"
                + " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 40, 0, 0, 10);"
        );

        Label icon = new Label(getIcon(type));
        icon.setStyle("-fx-font-size: 42px;");

        Label titleLabel = new Label(title);
        titleLabel.setStyle(
                "-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: white;"
        );
        titleLabel.setWrapText(true);

        Label msgLabel = new Label(message);
        msgLabel.setStyle(
                "-fx-font-size: 14px; -fx-text-fill: #b0b5c0;"
                + " -fx-text-alignment: center;"
        );
        msgLabel.setWrapText(true);
        msgLabel.setMaxWidth(340);

        Button primaryBtn = new Button(primaryBtnText);
        primaryBtn.getStyleClass().add("primary-button");
        primaryBtn.setStyle(primaryBtn.getStyle() + " -fx-min-width: 120;");
        primaryBtn.setOnAction(_ -> {
            close();
            if (onPrimary != null) onPrimary.run();
        });

        Button secondaryBtn = new Button(secondaryBtnText);
        secondaryBtn.getStyleClass().add("secondary-button");
        secondaryBtn.setStyle(secondaryBtn.getStyle() + " -fx-min-width: 120;");
        secondaryBtn.setOnAction(_ -> {
            close();
            if (onSecondary != null) onSecondary.run();
        });

        HBox btnBox = new HBox(12, secondaryBtn, primaryBtn);
        btnBox.setAlignment(Pos.CENTER);

        card.getChildren().addAll(icon, titleLabel, msgLabel, btnBox);
        getChildren().add(card);

        setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), this);
        fadeIn.setToValue(1);
        fadeIn.play();

        ScaleTransition scaleIn = new ScaleTransition(Duration.millis(200), card);
        scaleIn.setFromX(0.85);
        scaleIn.setFromY(0.85);
        scaleIn.setToX(1);
        scaleIn.setToY(1);
        scaleIn.play();
    }

    private void close() {
        FadeTransition fadeOut = new FadeTransition(Duration.millis(150), this);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(_ -> {
            if (getParent() instanceof StackPane parent) {
                parent.getChildren().remove(this);
            }
            if (onClose != null) onClose.run();
        });
        fadeOut.play();
    }

    public static void show(
            StackPane root,
            DialogType type,
            String title,
            String message
    ) {
        StyledDialog dialog = new StyledDialog(type, title, message, null);
        root.getChildren().add(dialog);
    }

    public static void show(
            StackPane root,
            DialogType type,
            String title,
            String message,
            Runnable onDismiss
    ) {
        StyledDialog dialog = new StyledDialog(type, title, message, onDismiss);
        root.getChildren().add(dialog);
    }

    private static String getIcon(DialogType type) {
        return switch (type) {
            case SUCCESS -> "✅";
            case WARNING -> "❗";
            case ERROR -> "❌";
            case INFO -> "💬";
        };
    }
}
