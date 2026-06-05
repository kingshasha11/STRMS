package src.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.geometry.Pos;
import javafx.beans.property.SimpleStringProperty;

import src.Admin;
import src.Engineer;
import src.Manager;
import src.Task;
import src.TaskHistoryEntry;
import src.TaskManager;
import src.User;
import src.enumeration.TaskStatus;
import src.exceptions.InvalidRoleException;
import src.exceptions.InvalidTaskStateException;
import src.exceptions.TaskNotFoundException;

import java.io.IOException;
import java.util.Map;

public class TaskDetailController {

    // ── Composants FXML — En-tête ──────────────────────────────────────────
    @FXML private Label  taskTitleLabel;
    @FXML private Button assignBtn;
    @FXML private Button startBtn;
    @FXML private Button completeBtn;
    @FXML private Button deleteBtn;

    // ── Composants FXML — Panneau gauche ──────────────────────────────────
    @FXML private Label    statusLabel;
    @FXML private Label    priorityLabel;
    @FXML private Label    categoryLabel;
    @FXML private Label    assigneeLabel;
    @FXML private Label    deadlineLabel;
    @FXML private TextArea descArea;
    @FXML private ListView<String> depsListView;

    // ── Composants FXML — Panneau droit (historique) ───────────────────────
    @FXML private TableView<TaskHistoryEntry>           historyTable;
    @FXML private TableColumn<TaskHistoryEntry, String> colTimestamp;
    @FXML private TableColumn<TaskHistoryEntry, String> colUser;
    @FXML private TableColumn<TaskHistoryEntry, String> colAction;

    // ── Dépendances injectées ──────────────────────────────────────────────
    private TaskManager         taskManager;
    private User                currentUser;
    private Map<String, String> passwords;
    private Task                task;

    // ── Setters d'injection ───────────────────────────────────────────────
    public void setTaskManager(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    public void setPasswords(Map<String, String> passwords) {
        this.passwords = passwords;
    }

    public void setTask(Task task) {
        this.task = task;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Initialisation
    // ══════════════════════════════════════════════════════════════════════

    public void initialize() {
        if (task == null) return;

        fillTaskInfo();
        fillDependencies();
        fillHistory();
        configureButtonsForRole();
    }

    private void fillTaskInfo() {
        taskTitleLabel.setText(task.getTitle());
        descArea.setText(task.getDescription());

        // Statut
        String status = task.getTaskStatus().name().replace("_", " ");
        statusLabel.setText(status);
        statusLabel.setStyle(switch (task.getTaskStatus().name()) {
            case "TODO"        -> "-fx-text-fill: #888888; -fx-font-weight: bold;";
            case "IN_PROGRESS" -> "-fx-text-fill: #185FA5; -fx-font-weight: bold;";
            case "BLOCKED"     -> "-fx-text-fill: #BA7517; -fx-font-weight: bold;";
            case "DONE"        -> "-fx-text-fill: #3B6D11; -fx-font-weight: bold;";
            default            -> "";
        });

        // Priorité
        priorityLabel.setText(task.getPriorityLevel().name());
        priorityLabel.setStyle(switch (task.getPriorityLevel().name()) {
            case "CRITICAL" -> "-fx-text-fill: #A32D2D; -fx-font-weight: bold;";
            case "HIGH"     -> "-fx-text-fill: #BA7517; -fx-font-weight: bold;";
            case "MEDIUM"   -> "-fx-text-fill: #185FA5;";
            default         -> "-fx-text-fill: #888888;";
        });

        categoryLabel.setText(task.getTaskCategory().name());
        
        // Prise en charge du polymorphisme User pour l'affichage de l'assignation
        User assignedUser = task.getAssignedUser();
        assigneeLabel.setText(assignedUser != null ? assignedUser.getName() + " (" + assignedUser.getRole() + ")" : "Non assigné");
        
        deadlineLabel.setText(task.getDeadline() != null ? task.getDeadline().toString() : "Aucune échéance");
    }

    private void fillDependencies() {
        ObservableList<String> deps = FXCollections.observableArrayList();

        if (task.getDependencies().isEmpty()) {
            deps.add("Aucune dépendance");
        } else {
            for (Task dep : task.getDependencies()) {
                String line = dep.getId() + " — " + dep.getTitle()
                    + "  [" + dep.getTaskStatus().name().replace("_", " ") + "]";
                deps.add(line);
            }
        }

        depsListView.setItems(deps);
        depsListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null); setStyle("");
                } else {
                    setText(item);
                    if (item.contains("DONE")) setStyle("-fx-text-fill: #3B6D11;");
                    else if (item.contains("BLOCKED")) setStyle("-fx-text-fill: #BA7517;");
                    else if (item.contains("IN PROGRESS")) setStyle("-fx-text-fill: #185FA5;");
                    else setStyle("-fx-text-fill: #888888;");
                }
            }
        });
    }

    private void fillHistory() {
        colTimestamp.setCellValueFactory(data -> new SimpleStringProperty(
            data.getValue().getTimestamp() != null ? data.getValue().getTimestamp().toString() : ""));

        colUser.setCellValueFactory(data -> new SimpleStringProperty(
            data.getValue().getPerformedBy() != null ? data.getValue().getPerformedBy().getName() : "Système"));

        colAction.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getAction()));

        ObservableList<TaskHistoryEntry> history = FXCollections.observableArrayList(task.getHistory());
        historyTable.setItems(history);
        historyTable.setPlaceholder(new Label("Aucune action enregistrée."));
    }

    private void configureButtonsForRole() {
        boolean isAdmin    = currentUser instanceof Admin;
        boolean isManager  = currentUser instanceof Manager;
        boolean isDone     = task.getTaskStatus() == TaskStatus.DONE;
        
        // Vérification de l'affectation basée sur l'identifiant de l'utilisateur (Polymorphe)
        boolean isAssignedUser = task.getAssignedUser() != null 
            && task.getAssignedUser().getId().equals(currentUser.getId());

        assignBtn.setVisible((isAdmin || isManager) && !isDone);
        assignBtn.setManaged((isAdmin || isManager) && !isDone);

        boolean canStart = isAssignedUser && (task.getTaskStatus() == TaskStatus.TODO || task.getTaskStatus() == TaskStatus.BLOCKED);
        startBtn.setVisible(canStart);
        startBtn.setManaged(canStart);

        boolean canComplete = isAssignedUser && task.getTaskStatus() == TaskStatus.IN_PROGRESS;
        completeBtn.setVisible(canComplete);
        completeBtn.setManaged(canComplete);

        deleteBtn.setVisible(isAdmin);
        deleteBtn.setManaged(isAdmin);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Actions des boutons
    // ══════════════════════════════════════════════════════════════════════

    @FXML
    private void handleBack() {
        navigateToDashboardOverview();
    }

    @FXML
    private void handleAssign() {
        ChoiceDialog<String> dialog = new ChoiceDialog<>();
        dialog.setTitle("Assigner la tâche");
        dialog.setHeaderText("Choisir un collaborateur pour : " + task.getTitle());
        dialog.setContentText("Collaborateur :");

        // On inclut les Engineers dans la liste de choix possible
        taskManager.getUsers().values().stream()
            .filter(u -> u instanceof Engineer)
            .map(u -> u.getName() + " (" + u.getRole() + ")")
            .forEach(displayName -> dialog.getItems().add(displayName));

        dialog.showAndWait().ifPresent(chosenDisplay -> {
            // Récupération de l'utilisateur sélectionné
            User selectedUser = taskManager.getUsers().values().stream()
                .filter(u -> (u.getName() + " (" + u.getRole() + ")").equals(chosenDisplay))
                .findFirst().orElse(null);

            if (selectedUser != null) {
                try {
                    taskManager.assignTask(task.getId(), selectedUser, currentUser);
                    refresh();
                } catch (Exception e) {
                    showAlert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
                }
            }
        });
    }

    @FXML
    private void handleStart() {
        try {
            taskManager.updateTask(task.getId(), TaskStatus.IN_PROGRESS, currentUser);
            refresh();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    private void handleComplete() {
        try {
            taskManager.completeTask(task.getId(), currentUser);
            refresh();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    @FXML
    private void handleDelete() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Supprimer la tâche \"" + task.getTitle() + "\" ?", ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirmer la suppression");
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                try {
                    taskManager.deleteTask(task.getId(), currentUser);
                    navigateToDashboardOverview();
                } catch (Exception e) {
                    showAlert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
                }
            }
        });
    }

    private void refresh() {
        try {
            this.task = taskManager.findTask(task.getId());
            fillTaskInfo();
            fillDependencies();
            fillHistory();
            configureButtonsForRole();
        } catch (TaskNotFoundException e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Navigation Centralisée (Respect du Main Layout)
    // ══════════════════════════════════════════════════════════════════════

    private void navigateToDashboardOverview() {
        // Recharger la vue centrale d'origine
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("../view/DashboardView.fxml"));
            Parent root = loader.load();
            DashboardController dc = loader.getController();
            dc.setTaskManager(taskManager);
            dc.setCurrentUser(currentUser);
            dc.setPasswords(passwords);
            dc.initialize();
            Stage stage = (Stage) deleteBtn.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("STRMS — " + currentUser.getName());
            stage.show();
        } catch (IOException e) {
            // Fallback : simple refresh
            showAlert(Alert.AlertType.ERROR, "Erreur de navigation", "Impossible de retourner au tableau de bord.");
            e.printStackTrace();
        }

        /* try {
            BorderPane mainBorderPane = (BorderPane) taskTitleLabel.getScene().getRoot();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("../view/DashboardView.fxml"));
            
            Parent dashboardView = loader.load();
            DashboardController dc = loader.getController();

            dc.setTaskManager(taskManager);
            dc.setCurrentUser(currentUser);
            dc.setPasswords(passwords);
            dc.initialize();

            mainBorderPane.setCenter(dashboardView);

        } catch (IOException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Erreur de navigation", "Impossible de retourner au tableau de bord.");
        } */
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type, content, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
