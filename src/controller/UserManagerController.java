package src.controller;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import src.Admin;
import src.Engineer;
import src.Manager;
import src.TaskManager;
import src.User;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class UserManagerController {

    // ── Composants FXML ───────────────────────────────────────────────────
    @FXML private TableView<User>           userTable;
    @FXML private TableColumn<User, String> colId;
    @FXML private TableColumn<User, String> colName;
    @FXML private TableColumn<User, String> colEmail;
    @FXML private TableColumn<User, String> colRole;
    @FXML private TableColumn<User, Number> colTasks;
    @FXML private TableColumn<User, Void>   colActions;

    // ── Dépendances injectées ──────────────────────────────────────────────
    private TaskManager         taskManager;
    private User                currentUser;
    private Map<String, String> passwords;

    public void setTaskManager(TaskManager taskManager) { this.taskManager = taskManager; }
    public void setCurrentUser(User user)               { this.currentUser = user; }
    public void setPasswords(Map<String, String> p)     { this.passwords = p; }

    // ══════════════════════════════════════════════════════════════════════
    // Initialisation
    // ══════════════════════════════════════════════════════════════════════

    public void initialize() {
        if (taskManager == null) return;

        // Colonnes simples
        colId.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getId()));
        colName.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getName()));
        colEmail.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getEmail()));

        // Rôle avec badge coloré
        colRole.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getRole()));
        colRole.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String role, boolean empty) {
                super.updateItem(role, empty);
                if (empty || role == null) {
                    setText(null); setStyle("");
                } else {
                    setText(role);
                    setStyle(switch (role) {
                        case "Admin"    -> "-fx-text-fill:#3C3489;-fx-font-weight:bold;";
                        case "Manager"  -> "-fx-text-fill:#185FA5;-fx-font-weight:bold;";
                        default         -> "-fx-text-fill:#3B6D11;-fx-font-weight:bold;";
                    });
                }
            }
        });

        // Nombre de tâches assignées — utilise getUserAssignedTasksCount()
        colTasks.setCellValueFactory(c ->
            new SimpleIntegerProperty(
                taskManager.getUserAssignedTasksCount(c.getValue().getId())));

        setupActionsColumn();
    }

    // ── Chargement des données ─────────────────────────────────────────────
    public void loadUserData() {
        if (taskManager == null) return;
        userTable.setItems(
            FXCollections.observableArrayList(taskManager.getAllUsers()));
    }

    // ── Colonne Actions ────────────────────────────────────────────────────

    private void setupActionsColumn() {
        colActions.setCellFactory(param -> new TableCell<User, Void>() {
            private final Button deleteBtn = new Button("Supprimer");
            private final HBox   container = new HBox(deleteBtn);
            {
                container.setAlignment(Pos.CENTER);
                deleteBtn.setStyle(
                    "-fx-background-color:#A32D2D;-fx-text-fill:white;" +
                    "-fx-font-size:11;-fx-padding:3 8;");
                deleteBtn.setOnAction(e -> {
                    User target = getTableView().getItems().get(getIndex());
                    handleDeleteUser(target);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    // Masquer le bouton si c'est l'utilisateur courant
                    boolean isSelf = getTableView().getItems().get(getIndex())
                        .getId().equals(currentUser.getId());
                    deleteBtn.setDisable(isSelf);
                    setGraphic(container);
                }
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    // Bouton "+" — Ajouter un utilisateur
    // ══════════════════════════════════════════════════════════════════════

    @FXML
    private void openAddUserDialog() {
        // Seul l'Admin peut créer des utilisateurs
        if (!(currentUser instanceof Admin)) {
            showAlert(Alert.AlertType.WARNING, "Accès refusé",
                "Seuls les Admins peuvent créer des utilisateurs.");
            return;
        }

        Dialog<User> dialog = new Dialog<>();
        dialog.setTitle("Création d'un collaborateur");
        dialog.setHeaderText("Saisissez les informations du nouveau membre :");

        ButtonType saveBtn = new ButtonType("Créer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        // Formulaire
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setStyle("-fx-padding:20;");

        TextField    nameF  = new TextField();
        nameF.setPromptText("Nom complet");
        TextField    emailF = new TextField();
        emailF.setPromptText("prenom@strms.fr");
        PasswordField passF = new PasswordField();
        passF.setPromptText("Mot de passe");
        ComboBox<String> roleCombo = new ComboBox<>(
            FXCollections.observableArrayList("Engineer", "Manager", "Admin"));
        roleCombo.setValue("Engineer");
        roleCombo.setMaxWidth(Double.MAX_VALUE);

        grid.add(new Label("Nom :"),         0, 0); grid.add(nameF,    1, 0);
        grid.add(new Label("Email :"),       0, 1); grid.add(emailF,   1, 1);
        grid.add(new Label("Mot de passe :"),0, 2); grid.add(passF,    1, 2);
        grid.add(new Label("Rôle :"),        0, 3); grid.add(roleCombo,1, 3);

        dialog.getDialogPane().setContent(grid);

        // Conversion résultat → objet User
        dialog.setResultConverter(btn -> {
            if (btn != saveBtn) return null;

            String name  = nameF.getText().trim();
            String email = emailF.getText().trim().toLowerCase();
            String pass  = passF.getText();
            String role  = roleCombo.getValue();

            // Validation
            if (name.isEmpty() || email.isEmpty() || pass.isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Champs vides",
                    "Tous les champs sont obligatoires.");
                return null;
            }
            if (!email.contains("@")) {
                showAlert(Alert.AlertType.WARNING, "Email invalide",
                    "L'adresse email doit contenir '@'.");
                return null;
            }
            if (passwords != null && passwords.containsKey(email)) {
                showAlert(Alert.AlertType.ERROR, "Email déjà utilisé",
                    "Un compte existe déjà avec cet email.");
                return null;
            }

            // Enregistrement du mot de passe dans la Map
            if (passwords != null) passwords.put(email, pass);

            // Création de l'objet User selon le rôle
            String id = "U-" + UUID.randomUUID().toString().substring(0, 5).toUpperCase();
            return switch (role) {
                case "Admin"   -> new Admin(id, name, email);
                case "Manager" -> new Manager(id, name, email, "DevTeam");
                default        -> new Engineer(id, name, email, "General");
            };
        });

        Optional<User> result = dialog.showAndWait();
        result.ifPresent(user -> {
            if (user == null) return;
            try {
                taskManager.addUser(user, currentUser);
                loadUserData(); // Rafraîchir le tableau
                showAlert(Alert.AlertType.INFORMATION, "Succès",
                    "L'utilisateur " + user.getName() + " a été créé avec succès.");
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
            }
        });
    }

    // ── Suppression ────────────────────────────────────────────────────────

    private void handleDeleteUser(User target) {
        if (target.getId().equals(currentUser.getId())) {
            showAlert(Alert.AlertType.WARNING, "Opération impossible",
                "Vous ne pouvez pas supprimer votre propre compte actif.");
            return;
        }

        // Vérifier si l'utilisateur a des tâches IN_PROGRESS
        int activeTasks = taskManager.getUserAssignedTasksCount(target.getId());
        String msg = "Supprimer " + target.getName() + " (" + target.getRole() + ") ?\n";
        if (activeTasks > 0) {
            msg += "\n⚠ Attention : " + activeTasks + " tâche(s) assignée(s) seront désassignées.";
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            msg, ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirmer la suppression");
        confirm.setHeaderText(null);

        if (confirm.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
            try {
                taskManager.deleteUser(target.getId(), currentUser);
                if (passwords != null) passwords.remove(target.getEmail().toLowerCase());
                loadUserData();
                showAlert(Alert.AlertType.INFORMATION, "Supprimé",
                    target.getName() + " a été supprimé du système.");
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "Erreur", e.getMessage());
            }
        }
    }

    // ── Utilitaire ─────────────────────────────────────────────────────────

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type, content, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}