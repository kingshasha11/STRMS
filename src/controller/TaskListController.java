package src.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.geometry.Pos;

import src.Admin;
import src.Manager;
import src.Task;
import src.TaskManager;
import src.User;
import src.enumeration.PriorityLevel;
import src.enumeration.TaskCategory;
import src.enumeration.TaskStatus;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

public class TaskListController {

    // ── Composants FXML ───────────────────────────────────────────────────
    @FXML private TextField  searchField;
    @FXML private ComboBox<String>  filterStatus;
    @FXML private ComboBox<String>  filterPriority;
    @FXML private ComboBox<String>  filterCategory;
    @FXML private Button     newTaskBtn;

    @FXML private TableView<Task>           taskTable;
    @FXML private TableColumn<Task, String> colId;
    @FXML private TableColumn<Task, String> colTitle;
    @FXML private TableColumn<Task, String> colStatus;
    @FXML private TableColumn<Task, String> colPriority;
    @FXML private TableColumn<Task, String> colCategory;
    @FXML private TableColumn<Task, String> colAssignee;
    @FXML private TableColumn<Task, String> colDeadline;
    @FXML private TableColumn<Task, Void>   colActions;

    @FXML private Label taskCountLabel;

    // ── Dépendances injectées ──────────────────────────────────────────────
    private TaskManager         taskManager;
    private User                currentUser;
    private Map<String, String> passwords;

    // ── Données ───────────────────────────────────────────────────────────
    private ObservableList<Task> allTasks;
    private FilteredList<Task>   filteredTasks;

    public void setTaskManager(TaskManager tm)         { this.taskManager = tm; }
    public void setCurrentUser(User u)                  { this.currentUser = u; }
    public void setPasswords(Map<String, String> p)     { this.passwords = p; }

    // ══════════════════════════════════════════════════════════════════════
    // Initialisation
    // ══════════════════════════════════════════════════════════════════════

    public void initialize() {
        // Bouton Nouvelle tâche : Admin seulement
        boolean canCreate = currentUser instanceof Admin || currentUser instanceof Manager;
        newTaskBtn.setVisible(canCreate);
        newTaskBtn.setManaged(canCreate);

        populateFilters();
        configureColumns();
        loadTasks();
        setupFilters();
    }

    private void populateFilters() {
        filterStatus.setItems(FXCollections.observableArrayList(
            "Tous", "TODO", "BLOCKED", "IN_PROGRESS", "DONE"));
        filterStatus.setValue("Tous");

        filterPriority.setItems(FXCollections.observableArrayList(
            "Tous", "LOW", "MEDIUM", "HIGH", "CRITICAL"));
        filterPriority.setValue("Tous");

        filterCategory.setItems(FXCollections.observableArrayList(
            "Tous", "BUGFIX", "FEATURE", "DOCUMENTATION", "RESEARCH"));
        filterCategory.setValue("Tous");
    }

    private void configureColumns() {
        colId.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getId()));
        colTitle.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTitle()));

        colStatus.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getTaskStatus().name().replace("_", " ")));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setText(null); setStyle(""); }
                else {
                    setText(s);
                    setStyle(switch (s.replace(" ", "_")) {
                        case "TODO"        -> "-fx-text-fill:#888888;-fx-font-weight:bold;";
                        case "IN_PROGRESS" -> "-fx-text-fill:#185FA5;-fx-font-weight:bold;";
                        case "BLOCKED"     -> "-fx-text-fill:#BA7517;-fx-font-weight:bold;";
                        case "DONE"        -> "-fx-text-fill:#3B6D11;-fx-font-weight:bold;";
                        default -> "";
                    });
                }
            }
        });

        colPriority.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getPriorityLevel().name()));
        colPriority.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) { setText(null); setStyle(""); }
                else {
                    setText(p);
                    setStyle(switch (p) {
                        case "CRITICAL" -> "-fx-text-fill:#A32D2D;-fx-font-weight:bold;";
                        case "HIGH"     -> "-fx-text-fill:#BA7517;-fx-font-weight:bold;";
                        case "MEDIUM"   -> "-fx-text-fill:#185FA5;";
                        default         -> "-fx-text-fill:#888888;";
                    });
                }
            }
        });

        colCategory.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getTaskCategory().name()));

        colAssignee.setCellValueFactory(d -> {
            var u = d.getValue().getAssignedUser();
            return new SimpleStringProperty(u != null ? u.getName() + " (" + u.getRole() + ")" : "Non assigné");
        });

        colDeadline.setCellValueFactory(d -> {
            var dl = d.getValue().getDeadline();
            return new SimpleStringProperty(dl != null ? dl.toString() : "—");
        });

        setupActionsColumn();
    }

    private void setupActionsColumn() {
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button detailBtn = new Button("Détail");
            private final Button editBtn   = new Button("Modifier");
            private final HBox   box       = new HBox(4, detailBtn, editBtn);

            {
                box.setAlignment(Pos.CENTER);
                detailBtn.setStyle("-fx-background-color:#185FA5;-fx-text-fill:white;-fx-padding:3 6;");
                editBtn.setStyle("-fx-background-color:#3B6D11;-fx-text-fill:white;-fx-padding:3 6;");

                detailBtn.setOnAction(e -> {
                    Task task = getTableView().getItems().get(getIndex());
                    openTaskDetail(task);
                });
                editBtn.setOnAction(e -> {
                    Task task = getTableView().getItems().get(getIndex());
                    openTaskForm(task);
                });

                // Masquer "Modifier" si l'utilisateur est engineer
                boolean canEdit = currentUser instanceof Admin || currentUser instanceof Manager;
                editBtn.setVisible(canEdit);
                editBtn.setManaged(canEdit);
            }

            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });
    }

    private void loadTasks() {
        allTasks      = FXCollections.observableArrayList(taskManager.getAllTasks());
        filteredTasks = new FilteredList<>(allTasks, t -> true);
        taskTable.setItems(filteredTasks);
        taskTable.setPlaceholder(new Label("Aucune tâche à afficher."));
        updateCountLabel();
    }

    private void setupFilters() {
        searchField.textProperty().addListener((obs, old, val) -> applyFilters());
        filterStatus.valueProperty().addListener((obs, old, val) -> applyFilters());
        filterPriority.valueProperty().addListener((obs, old, val) -> applyFilters());
        filterCategory.valueProperty().addListener((obs, old, val) -> applyFilters());
    }

    private void applyFilters() {
        String search   = searchField.getText().toLowerCase().trim();
        String status   = filterStatus.getValue();
        String priority = filterPriority.getValue();
        String category = filterCategory.getValue();

        filteredTasks.setPredicate(t -> {
            boolean matchSearch   = search.isEmpty()
                || t.getTitle().toLowerCase().contains(search)
                || t.getId().toLowerCase().contains(search);
            boolean matchStatus   = "Tous".equals(status)
                || t.getTaskStatus().name().equals(status);
            boolean matchPriority = "Tous".equals(priority)
                || t.getPriorityLevel().name().equals(priority);
            boolean matchCategory = "Tous".equals(category)
                || t.getTaskCategory().name().equals(category);
            return matchSearch && matchStatus && matchPriority && matchCategory;
        });

        updateCountLabel();
    }

    private void updateCountLabel() {
        taskCountLabel.setText(filteredTasks.size() + " tâche(s) affichée(s) / "
            + taskManager.getTasks().size() + " au total");
    }

    // ── Actions ───────────────────────────────────────────────────────────

    @FXML
    private void openTaskForm() { openTaskForm(null); }

    private void openTaskForm(Task taskToEdit) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("../view/TaskFormView.fxml"));
            Parent root = loader.load();

            TaskFormController fc = loader.getController();
            fc.setTaskManager(taskManager);
            fc.setCurrentUser(currentUser);
            fc.setPasswords(passwords);
            if (taskToEdit != null) fc.setTask(taskToEdit);
            fc.initialize();

            BorderPane mainPane = (BorderPane) taskTable.getScene().getRoot();
            mainPane.setCenter(root);

        } catch (IOException e) {
            showAlert("Erreur", "Impossible d'ouvrir le formulaire : " + e.getMessage());
        }
    }

    private void openTaskDetail(Task task) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("../view/TaskDetailsView.fxml"));
            Parent root = loader.load();

            TaskDetailController dc = loader.getController();
            dc.setTaskManager(taskManager);
            dc.setCurrentUser(currentUser);
            dc.setPasswords(passwords);
            dc.setTask(task);
            dc.initialize();

            BorderPane mainPane = (BorderPane) taskTable.getScene().getRoot();
            mainPane.setCenter(root);

        } catch (IOException e) {
            showAlert("Erreur", "Impossible d'ouvrir le détail : " + e.getMessage());
        }
    }

    private void showAlert(String title, String content) {
        Alert a = new Alert(Alert.AlertType.ERROR, content, ButtonType.OK);
        a.setTitle(title); a.setHeaderText(null); a.showAndWait();
    }
}