package src.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import src.TaskManager;
import src.User;

import java.io.IOException;
import java.util.Map;

public class LoginController {

    // ── Composants injectés depuis le FXML ────────────────────────────────
    @FXML private TextField     emailField;
    @FXML private PasswordField passwordField;
    @FXML private Label         errorLabel;

    // ── Dépendances injectées depuis Main ─────────────────────────────────
    private TaskManager         taskManager;
    private Map<String, String> passwords;

    public void setTaskManager(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    public void setPasswords(Map<String, String> passwords) {
        this.passwords = passwords;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Action du bouton "Se connecter"
    // ══════════════════════════════════════════════════════════════════════

    @FXML
    private void handleLogin() {
        String email    = emailField.getText().trim().toLowerCase();
        String password = passwordField.getText();

        // ── 1. Champs vides ───────────────────────────────────────────────
        if (email.isEmpty() || password.isEmpty()) {
            showError("Veuillez remplir tous les champs.");
            return;
        }

        // ── 2. Recherche de l'utilisateur par email ───────────────────────
        User foundUser = taskManager.getUsers()
                .values()
                .stream()
                .filter(u -> u.getEmail().equalsIgnoreCase(email))
                .findFirst()
                .orElse(null);

        if (foundUser == null) {
            showError("Aucun compte trouvé pour cet email.");
            return;
        }

        // ── 3. Vérification du mot de passe ──────────────────────────────
        String expected = passwords.get(foundUser.getEmail().toLowerCase());
        if (expected == null || !expected.equals(password)) {
            showError("Mot de passe incorrect.");
            return;
        }

        // ── 4. Connexion réussie → Redirection Dashboard ──────────────────
        navigateToDashboard(foundUser);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Navigation vers DashboardView
    // ══════════════════════════════════════════════════════════════════════

    private void navigateToDashboard(User user) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("../view/DashboardView.fxml"));

            // 1. On charge d'abord le FXML. Le FXMLLoader va instancier automatiquement 
            // le DashboardController déclaré dans l'attribut fx:controller du FXML.
            Parent root = loader.load();

            // 2. On récupère l'instance unique créée par le Loader
            DashboardController dc = loader.getController();

            // 3. Injection séquentielle des dépendances capitales
            dc.setTaskManager(taskManager);
            dc.setCurrentUser(user);
            dc.setPasswords(passwords);

            // 4. On force l'initialisation des composants visuels dépendants des données 
            // (Barre d'accès selon le rôle, cartes KPI, TableView des tâches critiques)
            dc.initialize();

            // 5. Remplacement de la Scene (Bascule de l'écran d'authentification au Layout principal)
            Stage stage = (Stage) emailField.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("STRMS - " + user.getName());
            stage.show();

        } catch (IOException e) {
            showError("Erreur critique lors du chargement du tableau de bord.");
            e.printStackTrace();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Utilitaire
    // ══════════════════════════════════════════════════════════════════════

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}