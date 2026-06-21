package ui;

import dto.response.LeaderboardEntry;
import java.util.List;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import protocols.HttpClientWrapper;

@Slf4j
public class LeaderboardView extends VBox {
    public LeaderboardView(HttpClientWrapper httpClient, Runnable onBack) {
        setPadding(new Insets(20));
        setSpacing(20);
        setAlignment(Pos.CENTER);
        getStyleClass().add("container");

        Label titleLabel = new Label("🏆 Рейтинг гравців");
        titleLabel.getStyleClass().add("title-label");

        Label subtitle = new Label("Топ 10 найкращих гравців");
        subtitle.getStyleClass().add("stat-label");
        subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: rgba(255,255,255,0.45);");

        TableView<LeaderboardEntry> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        TableColumn<LeaderboardEntry, Integer> rankCol = new TableColumn<>("Місце");
        rankCol.setCellValueFactory(
                cell -> new SimpleIntegerProperty(cell.getValue().rank()).asObject()
        );
        rankCol.setMaxWidth(70);
        // Custom cell for medal icons on top 3
        rankCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    int index = getIndex() + 1;
                    String medal = switch (index) {
                        case 1 -> "🥇";
                        case 2 -> "🥈";
                        case 3 -> "🥉";
                        default -> String.valueOf(index);
                    };
                    setText(medal);
                    setStyle(item <= 3
                            ? "-fx-font-size: 18px; -fx-alignment: CENTER;"
                            : "-fx-alignment: CENTER;");
                }
            }
        });

        TableColumn<LeaderboardEntry, String> nameCol = new TableColumn<>("Гравець");
        nameCol.setCellValueFactory(
                cell -> new SimpleStringProperty(cell.getValue().username())
        );

        TableColumn<LeaderboardEntry, Integer> eloCol = new TableColumn<>("Elo");
        eloCol.setCellValueFactory(
                cell -> new SimpleIntegerProperty(cell.getValue().eloRating()).asObject()
        );
        eloCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(String.valueOf(item));
                    setStyle("-fx-text-fill: #a29bfe; -fx-font-weight: bold;");
                }
            }
        });

        TableColumn<LeaderboardEntry, Integer> matchesCol = new TableColumn<>("Матчі");
        matchesCol.setCellValueFactory(
                cell -> new SimpleIntegerProperty(cell.getValue().matchCount()).asObject()
        );

        table.getColumns().add(rankCol);
        table.getColumns().add(nameCol);
        table.getColumns().add(eloCol);
        table.getColumns().add(matchesCol);

        Button backButton = new Button("← Назад");
        backButton.getStyleClass().add("secondary-button");
        backButton.setOnAction(_ -> onBack.run());

        getChildren().addAll(titleLabel, subtitle, table, backButton);

        new Thread(() -> {
            try {
                List<LeaderboardEntry> topPlayers = httpClient.getLeaderboard();
                Platform.runLater(() -> {
                    ObservableList<LeaderboardEntry> data =
                            FXCollections.observableArrayList(topPlayers);
                    table.setItems(data);
                });
            } catch (Exception e) {
                log.error("Failed to fetch leaderboard", e);
            }
        }).start();
    }
}
