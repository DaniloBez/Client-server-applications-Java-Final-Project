package ui;

import dto.request.FindUsersRequest;
import dto.request.Pagination;
import dto.request.UserFilter;
import dto.response.AdminUserResponse;
import dto.response.PageResponse;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import protocols.HttpClientWrapper;

@Slf4j
public class AdminPanelView extends StackPane {
    private final HttpClientWrapper httpClient;
    private final TableView<AdminUserResponse> table;

    private int currentPage = 1;
    private int pageSize = 15;
    private int totalPages = 1;
    
    private final TextField searchField = new TextField();
    private final ComboBox<String> roleFilter = new ComboBox<>();
    private final ComboBox<String> banFilter = new ComboBox<>();
    private final ComboBox<Integer> pageSizeFilter = new ComboBox<>();
    
    private final Label pageLabel = new Label("Сторінка 1 з 1");
    private final Button prevButton = new Button("< Попередня");
    private final Button nextButton = new Button("Наступна >");

    public AdminPanelView(HttpClientWrapper httpClient, Runnable onBack) {
        this.httpClient = httpClient;

        VBox contentBox = new VBox(15);
        contentBox.setPadding(new Insets(20));
        contentBox.setSpacing(15);
        contentBox.setAlignment(Pos.CENTER);
        contentBox.getStyleClass().add("container");
        contentBox.setStyle("-fx-max-width: 920; -fx-max-height: 720;");

        setAlignment(Pos.CENTER);

        Label titleLabel = new Label("Панель Адміністратора: Гравці");
        titleLabel.getStyleClass().add("title-label");

        searchField.setPromptText("Пошук за іменем...");
        searchField.getStyleClass().add("text-field");
        searchField.setPrefWidth(200);

        roleFilter.getItems().addAll("Всі ролі", "ADMIN", "PLAYER");
        roleFilter.setValue("Всі ролі");

        banFilter.getItems().addAll("Всі статуси", "Активні", "Заблоковані");
        banFilter.setValue("Всі статуси");

        Button searchBtn = new Button("Шукати");
        searchBtn.getStyleClass().add("primary-button");
        searchBtn.setOnAction(_ -> {
            currentPage = 1;
            refreshData();
        });

        HBox filterBox = new HBox(10, searchField, roleFilter, banFilter, searchBtn);
        filterBox.setAlignment(Pos.CENTER);

        table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        TableColumn<AdminUserResponse, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(
                cell -> new SimpleIntegerProperty(cell.getValue().id()).asObject()
        );
        idCol.setMaxWidth(50);
        idCol.setMinWidth(30);

        TableColumn<AdminUserResponse, String> nameCol = new TableColumn<>("Гравець");
        nameCol.setCellValueFactory(
                cell -> new SimpleStringProperty(cell.getValue().username())
        );
        nameCol.setMinWidth(100);

        TableColumn<AdminUserResponse, String> statusCol = new TableColumn<>("Статус");
        statusCol.setCellValueFactory(
                cell -> new SimpleStringProperty(cell.getValue().status())
        );
        statusCol.setMinWidth(80);

        TableColumn<AdminUserResponse, Integer> matchesCol = new TableColumn<>("Матчі");
        matchesCol.setCellValueFactory(
                cell -> new SimpleIntegerProperty(cell.getValue().matchCount()).asObject()
        );
        matchesCol.setMinWidth(60);

        TableColumn<AdminUserResponse, Integer> eloCol = new TableColumn<>("Elo");
        eloCol.setCellValueFactory(
                cell -> new SimpleIntegerProperty(cell.getValue().eloRating()).asObject()
        );
        eloCol.setMinWidth(60);

        TableColumn<AdminUserResponse, String> createdAtCol = new TableColumn<>("Дата реєстрації");
        createdAtCol.setCellValueFactory(
                cell -> new SimpleStringProperty(cell.getValue().createdAt())
        );
        createdAtCol.setMinWidth(140);

        TableColumn<AdminUserResponse, AdminUserResponse> actionCol = new TableColumn<>("Дія");
        actionCol.setMinWidth(100);
        actionCol.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(param.getValue()));
        actionCol.setCellFactory(_ -> new TableCell<>() {
            private final Button actionBtn = new Button();

            {
                actionBtn.getStyleClass().add("secondary-button");
                actionBtn.setOnAction(_ -> {
                    AdminUserResponse user = getItem();
                    if (user != null) {
                        banUser(user.id(), !user.isBanned());
                    }
                });
            }

            @Override
            protected void updateItem(AdminUserResponse user, boolean empty) {
                super.updateItem(user, empty);
                if (empty || user == null)
                    setGraphic(null);
                else {
                    if (user.isBanned()) {
                        actionBtn.setText("Розблокувати");
                        actionBtn.setStyle(
                                "-fx-background-color: linear-gradient(to right, #51cf66, #40c057);"
                                + " -fx-text-fill: white;"
                                + " -fx-background-radius: 8; -fx-font-size: 12px;"
                        );
                    } else {
                        actionBtn.setText("Заблокувати");
                        actionBtn.setStyle(
                                "-fx-background-color: linear-gradient(to right, #ff6b6b, #ee5a24);"
                                + " -fx-text-fill: white;"
                                + " -fx-background-radius: 8; -fx-font-size: 12px;"
                        );
                    }
                    setGraphic(actionBtn);
                }
            }
        });

        table.getColumns().add(idCol);
        table.getColumns().add(nameCol);
        table.getColumns().add(statusCol);
        table.getColumns().add(matchesCol);
        table.getColumns().add(eloCol);
        table.getColumns().add(createdAtCol);
        table.getColumns().add(actionCol);

        pageSizeFilter.getItems().addAll(10, 15, 20, 50);
        pageSizeFilter.setValue(15);
        pageSizeFilter.setOnAction(_ -> {
            pageSize = pageSizeFilter.getValue();
            currentPage = 1;
            refreshData();
        });

        prevButton.getStyleClass().add("secondary-button");
        prevButton.setOnAction(_ -> {
            if (currentPage > 1) {
                currentPage--;
                refreshData();
            }
        });

        nextButton.getStyleClass().add("secondary-button");
        nextButton.setOnAction(_ -> {
            if (currentPage < totalPages) {
                currentPage++;
                refreshData();
            }
        });

        HBox paginationBox = new HBox(
                15,
                new Label("На сторінку:"),
                pageSizeFilter,
                prevButton,
                pageLabel,
                nextButton
        );
        paginationBox.setAlignment(Pos.CENTER);

        Button backButton = new Button("Назад");
        backButton.getStyleClass().add("secondary-button");
        backButton.setOnAction(_ -> onBack.run());

        Button refreshButton = new Button("Оновити");
        refreshButton.getStyleClass().add("primary-button");
        refreshButton.setOnAction(_ -> refreshData());

        HBox buttonBox = new HBox(10, backButton, refreshButton);
        buttonBox.setAlignment(Pos.CENTER);

        contentBox.getChildren().addAll(
                titleLabel, filterBox, table, paginationBox, buttonBox
        );
        getChildren().add(contentBox);

        refreshData();
    }

    private void banUser(int userId, boolean isBanned) {
        new Thread(() -> {
            try {
                httpClient.banUser(userId, isBanned);
                Platform.runLater(() -> {
                    StyledDialog.show(
                            this,
                            StyledDialog.DialogType.SUCCESS,
                            isBanned ? "Заблоковано" : "Розблоковано",
                            isBanned
                                    ? "Користувача заблоковано та відключено."
                                    : "Користувача розблоковано."
                    );
                    refreshData();
                });
            } catch (Exception e) {
                Platform.runLater(() ->
                        StyledDialog.show(
                                this,
                                StyledDialog.DialogType.ERROR,
                                "Не вдалося виконати дію",
                                e.getMessage()
                        )
                );
            }
        }).start();
    }

    private void refreshData() {
        try {
            String nameLike = searchField.getText().isBlank()
                    ? null
                    : "%" + searchField.getText() + "%";
            String role = "Всі ролі".equals(roleFilter.getValue()) ? null : roleFilter.getValue();
            
            Boolean isBanned = null;
            if ("Активні".equals(banFilter.getValue()))
                isBanned = false;
            else if ("Заблоковані".equals(banFilter.getValue()))
                isBanned = true;

            UserFilter filter = new UserFilter(
                    nameLike,
                    role,
                    isBanned,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
            Pagination pagination = new Pagination(currentPage, pageSize);
            FindUsersRequest request = new FindUsersRequest(filter, null, pagination);

            PageResponse<AdminUserResponse> pageResponse = httpClient.getAdminUsers(request);
            
            Platform.runLater(() -> {
                ObservableList<AdminUserResponse> data =
                        FXCollections.observableArrayList(pageResponse.items());
                table.setItems(data);
                
                totalPages = pageResponse.totalPages() == 0 ? 1 : pageResponse.totalPages();
                currentPage = pageResponse.currentPage();
                
                pageLabel.setText("Сторінка " + currentPage + " з " + totalPages);
                prevButton.setDisable(currentPage <= 1);
                nextButton.setDisable(currentPage >= totalPages);
            });
        } catch (Exception e) {
            log.error("Failed to fetch admin users", e);
        }
    }
}
