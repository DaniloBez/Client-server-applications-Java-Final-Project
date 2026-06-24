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
        subtitle.getStyleClass().add("leaderboard-subtitle");

        TableView<LeaderboardEntry> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        TableColumn<LeaderboardEntry, Integer> rankCol = new TableColumn<>("Місце");
        rankCol.setCellValueFactory(
                cell -> new SimpleIntegerProperty(cell.getValue().rank()).asObject()
        );
        rankCol.setMaxWidth(70);
        rankCol.setCellFactory(_ -> new TableCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    getStyleClass().removeAll(
                            "rank-cell",
                            "rank-gold",
                            "rank-silver",
                            "rank-bronze",
                            "rank-other"
                    );
                } else {
                    setText(String.valueOf(item));
                    getStyleClass().removeAll(
                            "rank-cell",
                            "rank-gold",
                            "rank-silver",
                            "rank-bronze",
                            "rank-other"
                    );
                    getStyleClass().add("rank-cell");
                    if (item == 1)
                        getStyleClass().add("rank-gold");
                    else if (item == 2)
                        getStyleClass().add("rank-silver");
                    else if (item == 3)
                        getStyleClass().add("rank-bronze");
                    else
                        getStyleClass().add("rank-other");
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
        eloCol.setCellFactory(_ -> new TableCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    getStyleClass().removeAll("leaderboard-elo");
                } else {
                    setText(String.valueOf(item));
                    getStyleClass().add("leaderboard-elo");
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
