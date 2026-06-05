package src.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.geometry.Pos;
import javafx.stage.Stage;

import src.Admin;
import src.Manager;
import src.Task;
import src.TaskManager;
import src.User;
import src.enumeration.PriorityLevel;
import src.enumeration.TaskStatus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DashboardController {

    @FXML private BorderPane mainBorderPane;

    @FXML private Button usersBtn;
    @FXML private Label  currentUserLabel;

    @FXML private VBox cardTodo;
    @FXML private VBox cardInProgress;
    @FXML private VBox cardBlocked;
    @FXML private VBox cardDone;

    @FXML private TableView<Task>           urgentTasksTable;
    @FXML private TableColumn<Task, String> colTitle;
    @FXML private TableColumn<Task, String> colPriority;
    @FXML private TableColumn<Task, String> colStatus;
    @FXML private TableColumn<Task, String> colAssignee;

    private TaskManager         taskManager;
    private User                currentUser;
    private Map<String, String> passwords;

    public void setTaskManager(TaskManager tm)         { this.taskManager = tm; }
    public void setCurrentUser(User u)                  { this.currentUser = u; }
    public void setPasswords(Map<String, String> p)     { this.passwords = p; }

    public void initialize() {
        configureRoleAccess();
        configureTableColumns();
        refreshDashboard();
    }

    private void configureRoleAccess() {
        if (currentUser != null) {
            boolean canManage = currentUser instanceof Admin || currentUser instanceof Manager;
            boolean isAdmin   = currentUser instanceof Admin;
            boolean isManager = currentUser instanceof Manager;
            usersBtn.setVisible(canManage);
            usersBtn.setManaged(canManage);
            String role = isAdmin ? "Admin" : isManager ? "Manager" : "Engineer";
            currentUserLabel.setText(currentUser.getName() + " - " + role);
        } else {
            currentUserLabel.setText("Mode Invité");
        }
    }

    private void configureTableColumns() {
        colTitle.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTitle()));

        colPriority.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getPriorityLevel().name()));
        colPriority.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) { setText(null); setStyle(""); } else {
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

        colStatus.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getTaskStatus().name()));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setText(null); setStyle(""); } else {
                    setText(s.replace("_", " "));
                    setStyle(switch (s) {
                        case "TODO"        -> "-fx-text-fill:#888888;-fx-font-weight:bold;";
                        case "IN_PROGRESS" -> "-fx-text-fill:#185FA5;-fx-font-weight:bold;";
                        case "BLOCKED"     -> "-fx-text-fill:#BA7517;-fx-font-weight:bold;";
                        case "DONE"        -> "-fx-text-fill:#3B6D11;-fx-font-weight:bold;";
                        default -> "";
                    });
                }
            }
        });

        colAssignee.setCellValueFactory(d -> {
            User u = d.getValue().getAssignedUser();
            return new SimpleStringProperty(u != null ? u.getName() + " (" + u.getRole() + ")" : "Non assigné");
        });

        urgentTasksTable.setPlaceholder(new Label("Aucune tâche critique en attente."));

        // Double-clic sur une tâche → détail
        urgentTasksTable.setRowFactory(tv -> {
            TableRow<Task> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    openTaskDetail(row.getItem());
                }
            });
            return row;
        });
    }

    public void refreshDashboard() {
        if (taskManager == null) return;
        refreshStatCards();
        refreshUrgentTasksTable();
    }

    private void refreshStatCards() {
        List<Task> all = taskManager.getAllTasks();
        buildCard(cardTodo,       "TODO",        all.stream().filter(t->t.getTaskStatus()==TaskStatus.TODO).count(),        "#888888");
        buildCard(cardInProgress, "IN PROGRESS", all.stream().filter(t->t.getTaskStatus()==TaskStatus.IN_PROGRESS).count(),"#185FA5");
        buildCard(cardBlocked,    "BLOCKED",     all.stream().filter(t->t.getTaskStatus()==TaskStatus.BLOCKED).count(),     "#BA7517");
        buildCard(cardDone,       "DONE",        all.stream().filter(t->t.getTaskStatus()==TaskStatus.DONE).count(),        "#3B6D11");
    }

    private void buildCard(VBox card, String label, long count, String color) {
        card.getChildren().clear();
        card.setAlignment(Pos.CENTER);
        card.setSpacing(6);
        card.setPrefWidth(140);
        card.setPrefHeight(80);
        card.setStyle(
            "-fx-background-color:white;" +
            "-fx-border-color:" + color + ";" +
            "-fx-border-width:2;-fx-border-radius:6;-fx-background-radius:6;" +
            "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.08),4,0,0,2);");
        Label cntLbl = new Label(String.valueOf(count));
        cntLbl.setStyle("-fx-font-size:28;-fx-font-weight:bold;-fx-text-fill:" + color + ";");
        Label namLbl = new Label(label);
        namLbl.setStyle("-fx-font-size:11;-fx-text-fill:" + color + ";");
        card.getChildren().addAll(cntLbl, namLbl);
    }

    private void refreshUrgentTasksTable() {
        List<Task> urgent = taskManager.getAllTasks().stream()
            .filter(t -> t.getTaskStatus() != TaskStatus.DONE)
            .filter(t -> t.getPriorityLevel() == PriorityLevel.CRITICAL
                      || t.getPriorityLevel() == PriorityLevel.HIGH)
            .sorted((a, b) -> {
                int c = a.compareTo(b);
                return c != 0 ? c : a.getTitle().compareTo(b.getTitle());
            })
            .collect(Collectors.toList());

        urgentTasksTable.setItems(FXCollections.observableArrayList(urgent));
    }

    // ── Navigation ────────────────────────────────────────────────────────

    @FXML private void showDashboard() {
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
            Stage stage = (Stage) mainBorderPane.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("STRMS — " + currentUser.getName());
            stage.show();

            /* FXMLLoader loader = new FXMLLoader(getClass().getResource("../view/DashboardView.fxml"));
            DashboardController dc = new DashboardController();
            dc.setTaskManager(taskManager);
            dc.setCurrentUser(currentUser);
            dc.setPasswords(passwords);
            loader.setController(dc);
            Parent root = loader.load();
            mainBorderPane.setCenter(((BorderPane) root).getCenter());
            dc.refreshDashboard(); */
        } catch (IOException e) {
            // Fallback : simple refresh
            refreshDashboard();
            e.printStackTrace();
        }
    } 
    @FXML private void showTasks()         { navigateTo("TaskListView.fxml"); }
    @FXML private void showReports()       { navigateTo("ReportView.fxml"); }
    @FXML private void showNotifications() { 
        showAlert(Alert.AlertType.INFORMATION, "Notifications",
            "Aucune notification pour l'instant."); 
    }

    @FXML
    private void showUsers() {
        if (!(currentUser instanceof Admin)) {
            showAlert(Alert.AlertType.WARNING, "Accès refusé",
                "Seuls les Admins peuvent accéder à la gestion des utilisateurs.");
            return;
        }
        navigateTo("UserManagerView.fxml");
    }

    @FXML
    private void handleLogout() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Voulez-vous vraiment vous déconnecter ?", ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Déconnexion");
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) navigateToLogin();
        });
    }

    private void navigateTo(String fxmlFile) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("../view/" + fxmlFile));

            Object ctrl = buildController(fxmlFile);
            if (ctrl != null) loader.setController(ctrl);

            Parent root = loader.load();

            if (ctrl != null) injectAndInit(ctrl);

            mainBorderPane.setCenter(root);

        } catch (IOException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Erreur de navigation",
                "Impossible de charger : " + fxmlFile + "\n" + e.getMessage());
        }
    }

    private Object buildController(String fxmlFile) {
        return switch (fxmlFile) {
            case "TaskListView.fxml" -> {
                TaskListController c = new TaskListController();
                c.setTaskManager(taskManager);
                c.setCurrentUser(currentUser);
                c.setPasswords(passwords);
                yield c;
            }
            case "DashboardView.fxml" -> {
                DashboardController c = new DashboardController();
                c.setTaskManager(taskManager);
                c.setCurrentUser(currentUser);
                c.setPasswords(passwords);
                yield c;
            }
            case "ReportView.fxml" -> {
                ReportController c = new ReportController();
                c.setTaskManager(taskManager);
                c.setCurrentUser(currentUser);
                yield c;
            }
            case "DependencyGraphView.fxml" -> {
                DependencyGraphController c = new DependencyGraphController();
                c.setTaskManager(taskManager);
                c.setCurrentUser(currentUser);
                c.setPasswords(passwords);
                yield c;
            }
            case "UserManagerView.fxml" -> {
                UserManagerController c = new UserManagerController();
                c.setTaskManager(taskManager);
                c.setCurrentUser(currentUser);
                c.setPasswords(passwords);
                yield c;
            }
            default -> null;
        };
    }

    private void injectAndInit(Object ctrl) {
        try {
            ctrl.getClass().getMethod("initialize").invoke(ctrl);
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            System.err.println("[DashboardController] init failed: " + e.getMessage());
        }
        // UserManagerController needs loadUserData() after initialize()
        try {
            ctrl.getClass().getMethod("loadUserData").invoke(ctrl);
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            System.err.println("[DashboardController] loadUserData failed: " + e.getMessage());
        }
    }

    private void navigateToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("../view/LoginView.fxml"));
            
            Parent root = loader.load();
            LoginController lc = loader.getController();
            //LoginController lc = new LoginController();
            lc.setTaskManager(taskManager);
            lc.setPasswords(passwords);
            //loader.setController(lc); 
            
            Stage stage = (Stage) mainBorderPane.getScene().getWindow();
            stage.setTitle("STRMS");
            stage.setScene(new Scene(root, 900, 650));
            stage.setMinWidth(700);
            stage.setMinHeight(500);
            stage.show();
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", "Impossible de charger l'écran de connexion.");
            e.printStackTrace();
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
            mainBorderPane.setCenter(root);
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert a = new Alert(type, content, ButtonType.OK);
        a.setTitle(title); a.setHeaderText(null); a.showAndWait();
    }
}