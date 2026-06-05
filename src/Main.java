package src;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import src.controller.LoginController;

import java.util.HashMap;
import java.util.Map;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {

        // ── 1. Système ────────────────────────────────────────────────────
        TaskManager taskManager = new TaskManager();

        // ── 2. Utilisateurs par défaut ────────────────────────────────────
        Admin    alice   = new Admin("U-001",    "Alice",   "alice@strms.fr",   1);
        Manager  bob     = new Manager("U-002",  "Bob",     "bob@strms.fr",     "DevTeam");
        Engineer charlie = new Engineer("U-003", "Charlie", "charlie@strms.fr", "Backend");
        Engineer dave    = new Engineer("U-004", "Dave",    "dave@strms.fr",    "Frontend");

        taskManager.addUser(alice);
        taskManager.addUser(bob);
        taskManager.addUser(charlie);
        taskManager.addUser(dave);

        // ── 3. Mots de passe (Map séparée — User n'a pas de password) ─────
        Map<String, String> passwords = new HashMap<>();
        passwords.put("alice@strms.fr",   "admin123");
        passwords.put("bob@strms.fr",     "manager123");
        passwords.put("charlie@strms.fr", "engineer123");
        passwords.put("dave@strms.fr",    "engineer123");

        // ── 4. Chargement de la persistance complète ──────────────────────
        // loadAll() charge tasks.csv + dependencies.csv + history.csv
        taskManager.loadAll();

        // ── 5. LoginView ──────────────────────────────────────────────────
        FXMLLoader loader = new FXMLLoader(
            getClass().getResource("view/LoginView.fxml"));
        Parent root = loader.load();

        LoginController lc = loader.getController();
        lc.setTaskManager(taskManager);
        lc.setPasswords(passwords);

        // ── 6. Fenêtre principale ─────────────────────────────────────────
        primaryStage.setTitle("STRMS");
        primaryStage.setScene(new Scene(root, 900, 650));
        primaryStage.setMinWidth(700);
        primaryStage.setMinHeight(500);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}