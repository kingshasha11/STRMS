package src.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import src.Task;
import src.TaskHistoryEntry;
import src.TaskManager;
import src.User;
import src.enumeration.PriorityLevel;
import src.enumeration.TaskCategory;
import src.enumeration.TaskStatus;
import src.exceptions.CircularDependencyException;

import java.io.IOException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class TaskFormController {

    @FXML private Label                    formTitle;
    @FXML private TextField                titleField;
    @FXML private TextArea                 descField;
    @FXML private ComboBox<PriorityLevel>  priorityCombo;
    @FXML private ComboBox<TaskCategory>   categoryCombo;
    @FXML private DatePicker               deadlinePicker;
    @FXML private ListView<String>         dependenciesList;
    @FXML private Label                    errorLabel;

    private TaskManager         taskManager;
    private User                currentUser;
    private Map<String, String> passwords;
    private Task                taskToEdit = null;

    public void setTaskManager(TaskManager tm)         { this.taskManager = tm; }
    public void setCurrentUser(User u)                  { this.currentUser = u; }
    public void setPasswords(Map<String, String> p)     { this.passwords = p; }
    public void setTask(Task task)                      { this.taskToEdit = task; }

    // ══════════════════════════════════════════════════════════════════════
    // Initialisation — appelée automatiquement par loader.load()
    // ══════════════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        if (taskManager == null) return; // garde-fou

        populateCombos();
        populateDependenciesList();
        dependenciesList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        if (taskToEdit != null) {
            // ── Mode édition ──────────────────────────────────────────────
            formTitle.setText("Modifier la tâche");
            titleField.setText(taskToEdit.getTitle());
            descField.setText(taskToEdit.getDescription());
            priorityCombo.setValue(taskToEdit.getPriorityLevel());
            categoryCombo.setValue(taskToEdit.getTaskCategory());

            if (taskToEdit.getDeadline() != null) {
                deadlinePicker.setValue(taskToEdit.getDeadline()
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate());
            }

            // Pré-sélectionner les dépendances existantes
            List<String> currentDepIds = taskToEdit.getDependencies().stream()
                .map(Task::getId)
                .collect(Collectors.toList());

            dependenciesList.getItems().forEach(item -> {
                String id = item.substring(0, item.indexOf(" |")).trim();
                if (currentDepIds.contains(id)) {
                    dependenciesList.getSelectionModel().select(item);
                }
            });

        } else {
            // ── Mode création ─────────────────────────────────────────────
            formTitle.setText("Nouvelle tâche");
        }
    }

    private void populateCombos() {
        priorityCombo.setItems(FXCollections.observableArrayList(PriorityLevel.values()));
        categoryCombo.setItems(FXCollections.observableArrayList(TaskCategory.values()));
    }

    private void populateDependenciesList() {
        List<String> items = taskManager.getTasks().values().stream()
            .filter(t -> taskToEdit == null || !t.getId().equals(taskToEdit.getId()))
            .map(t -> t.getId() + " | " + t.getTitle()
                    + " [" + t.getTaskStatus().name().replace("_", " ") + "]")
            .sorted()
            .collect(Collectors.toList());

        dependenciesList.setItems(FXCollections.observableArrayList(items));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Sauvegarde
    // ══════════════════════════════════════════════════════════════════════

    @FXML
    private void handleSave() {
        errorLabel.setVisible(false);

        String title = titleField.getText().trim();
        if (title.isEmpty()) {
            showError("Le titre est obligatoire."); return;
        }
        if (priorityCombo.getValue() == null) {
            showError("Veuillez sélectionner une priorité."); return;
        }
        if (categoryCombo.getValue() == null) {
            showError("Veuillez sélectionner une catégorie."); return;
        }

        Date deadline = null;
        if (deadlinePicker.getValue() != null) {
            deadline = Date.from(deadlinePicker.getValue()
                .atStartOfDay(ZoneId.systemDefault()).toInstant());
        }

        if (taskToEdit == null) handleCreate(title, deadline);
        else                    handleUpdate(title, deadline);
    }

    private void handleCreate(String title, Date deadline) {
        String id = "T-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        Task newTask = new Task(id, title, descField.getText().trim(),
            priorityCombo.getValue(), TaskStatus.TODO,
            categoryCombo.getValue(), deadline);
        try {
            taskManager.addTask(newTask, currentUser);
            if (!applyDependencies(newTask)) {
                taskManager.deleteTask(newTask.getId(), currentUser);
                return;
            }
            navigateToDashboard();
        } catch (Exception e) {
            showError("Erreur lors de la création : " + e.getMessage());
        }
    }

    private void handleUpdate(String title, Date deadline) {
        List<Task> initialDeps = new ArrayList<>(taskToEdit.getDependencies());
        try {
            // Nettoyer les anciennes dépendances
            for (Task dep : List.copyOf(taskToEdit.getDependencies()))
                taskManager.removeDependency(taskToEdit.getId(), dep.getId(), currentUser);

            // Appliquer les nouvelles
            if (!applyDependencies(taskToEdit)) {
                // Rollback
                for (Task dep : List.copyOf(taskToEdit.getDependencies()))
                    taskManager.removeDependency(taskToEdit.getId(), dep.getId(), currentUser);
                for (Task dep : initialDeps)
                    taskManager.addDependency(taskToEdit.getId(), dep.getId(), currentUser);
                return;
            }

            taskToEdit.setTitle(title);
            taskToEdit.updateDescription(descField.getText().trim());
            taskToEdit.changePriority(priorityCombo.getValue());
            taskToEdit.setDeadline(deadline);
            taskToEdit.addHistoryEntry(new TaskHistoryEntry(
                "Tâche modifiée par " + currentUser.getName(), currentUser));

            navigateToDashboard();
        } catch (Exception e) {
            showError("Erreur lors de la modification : " + e.getMessage());
        }
    }

    private boolean applyDependencies(Task task) {
        for (String selected : dependenciesList.getSelectionModel().getSelectedItems()) {
            String depId = selected.substring(0, selected.indexOf(" |")).trim();
            try {
                taskManager.addDependency(task.getId(), depId, currentUser);
            } catch (CircularDependencyException e) {
                showError("Dépendance circulaire avec : " + depId);
                return false;
            } catch (Exception e) {
                showError("Erreur dépendance : " + e.getMessage());
                return false;
            }
        }
        return true;
    }

    @FXML
    private void handleCancel() { navigateToDashboard(); }

    // ══════════════════════════════════════════════════════════════════════
    // Navigation — retour au Dashboard
    // ══════════════════════════════════════════════════════════════════════

    private void navigateToDashboard() {

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
            Stage stage = (Stage) formTitle.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("STRMS — " + currentUser.getName());
            stage.show();
        } catch (IOException e) {
            // Fallback : simple refresh
            showError("Erreur de chargement du tableau de bord : " + e.getMessage());
            e.printStackTrace();
        }

        /* try {
            // ── Récupérer le BorderPane racine de la scène ─────────────────
            // La scène entière a pour racine le BorderPane du Dashboard
            BorderPane mainBorderPane = (BorderPane) titleField.getScene().getRoot();

            // ── Charger un nouveau contenu Dashboard ───────────────────────
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("../view/DashboardView.fxml"));

            // DashboardView.fxml garde fx:controller → getController() après load()
            Parent dashboardView = loader.load();
            DashboardController dc = loader.getController();

            // Injecter AVANT d'appeler initialize() manuellement
            // (load() a déjà appelé initialize() automatiquement via fx:controller
            //  mais taskManager était null → on re-injecte et on force le refresh)
            dc.setTaskManager(taskManager);
            dc.setCurrentUser(currentUser);
            dc.setPasswords(passwords);
            dc.refreshDashboard(); // ← refreshDashboard() et NON initialize()
                                   //   pour éviter le double appel

            mainBorderPane.setCenter(dashboardView);

        } catch (ClassCastException e) {
            // La racine n'est pas un BorderPane — on est peut-être sur un écran isolé
            showError("Erreur de navigation : racine de scène inattendue.");
            e.printStackTrace();
        } catch (IOException e) {
            showError("Erreur de chargement du tableau de bord : " + e.getMessage());
            e.printStackTrace();
        } */
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}