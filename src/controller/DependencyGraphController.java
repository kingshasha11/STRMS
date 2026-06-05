package src.controller;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.shape.Polygon;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import src.Task;
import src.TaskManager;
import src.User;
import src.enumeration.TaskStatus;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class DependencyGraphController {

    // ── Composants FXML ───────────────────────────────────────────────────
    @FXML private CheckBox showDoneCheckbox;
    @FXML private Pane     graphPane;
    @FXML private HBox     nodeInfoPanel;
    @FXML private Label    nodeInfoLabel;

    // ── Dépendances injectées ──────────────────────────────────────────────
    private TaskManager         taskManager;
    private User                currentUser;
    private Map<String, String> passwords;

    // ── État interne ───────────────────────────────────────────────────────
    private Task selectedTask = null; // tâche cliquée par l'utilisateur

    // Couleurs par statut
    private static final Color COLOR_TODO        = Color.web("#888888");
    private static final Color COLOR_BLOCKED      = Color.web("#BA7517");
    private static final Color COLOR_IN_PROGRESS  = Color.web("#185FA5");
    private static final Color COLOR_DONE         = Color.web("#3B6D11");
    private static final Color COLOR_STROKE       = Color.web("#CCCCCC");
    private static final Color COLOR_EDGE         = Color.web("#AAAAAA");
    private static final Color COLOR_NODE_BG      = Color.WHITE;

    // Dimensions des nœuds
    private static final double NODE_RADIUS = 30;
    private static final double H_GAP       = 130; // espacement horizontal entre colonnes
    private static final double V_GAP       = 90;  // espacement vertical entre nœuds

    public void setTaskManager(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    public void setPasswords(Map<String, String> passwords) {
        this.passwords = passwords;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Initialisation
    // ══════════════════════════════════════════════════════════════════════

    public void initialize() {
        nodeInfoPanel.setVisible(false);
        refreshGraph();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Rafraîchissement du graphe (bouton + checkbox)
    // ══════════════════════════════════════════════════════════════════════

    @FXML
    private void refreshGraph() {
        graphPane.getChildren().clear();
        nodeInfoPanel.setVisible(false);
        selectedTask = null;

        // Filtrage selon la checkbox "Afficher tâches DONE"
        List<Task> tasks = taskManager.getTasks().values().stream()
            .filter(t -> showDoneCheckbox.isSelected()
                      || t.getTaskStatus() != TaskStatus.DONE)
            .collect(Collectors.toList());

        if (tasks.isEmpty()) {
            Text empty = new Text(20, 40, "Aucune tâche à afficher.");
            empty.setFill(COLOR_TODO);
            graphPane.getChildren().add(empty);
            return;
        }

        // Calcul des niveaux topologiques (BFS)
        Map<String, Integer> levels = computeLevels(tasks);

        // Calcul des positions X/Y de chaque nœud
        Map<String, double[]> positions = computePositions(tasks, levels);

        // Dessin des arêtes d'abord (sous les nœuds)
        drawEdges(tasks, positions);

        // Dessin des nœuds par-dessus
        drawNodes(tasks, positions);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Calcul des niveaux topologiques (BFS depuis les racines)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Attribue un niveau (colonne) à chaque tâche par BFS.
     * Les tâches sans dépendances sont au niveau 0.
     */
    private Map<String, Integer> computeLevels(List<Task> tasks) {
        Map<String, Integer> levels = new HashMap<>();
        Set<String> taskIds = tasks.stream()
            .map(Task::getId)
            .collect(Collectors.toSet());

        // Initialise toutes les tâches au niveau 0
        tasks.forEach(t -> levels.put(t.getId(), 0));

        // Propagation : si T dépend de D, level(T) = max(level(T), level(D) + 1)
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Task t : tasks) {
                for (Task dep : t.getDependencies()) {
                    if (!taskIds.contains(dep.getId())) continue; // ignoré si filtré
                    int proposed = levels.getOrDefault(dep.getId(), 0) + 1;
                    if (proposed > levels.get(t.getId())) {
                        levels.put(t.getId(), proposed);
                        changed = true;
                    }
                }
            }
        }
        return levels;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Calcul des positions X/Y
    // ══════════════════════════════════════════════════════════════════════

    private Map<String, double[]> computePositions(List<Task> tasks,
                                                    Map<String, Integer> levels) {
        Map<String, double[]> positions = new HashMap<>();

        // Grouper les tâches par niveau (colonne)
        Map<Integer, List<Task>> byLevel = tasks.stream()
            .collect(Collectors.groupingBy(t -> levels.getOrDefault(t.getId(), 0)));

        int maxLevel = byLevel.keySet().stream().mapToInt(i -> i).max().orElse(0);

        for (int level = 0; level <= maxLevel; level++) {
            List<Task> col = byLevel.getOrDefault(level, new ArrayList<>());
            double x = 60 + level * H_GAP;
            for (int row = 0; row < col.size(); row++) {
                double y = 60 + row * V_GAP;
                positions.put(col.get(row).getId(), new double[]{x, y});
            }
        }

        // Ajuster la taille du Pane
        double maxX = positions.values().stream()
            .mapToDouble(p -> p[0]).max().orElse(400) + NODE_RADIUS + 20;
        double maxY = positions.values().stream()
            .mapToDouble(p -> p[1]).max().orElse(300) + NODE_RADIUS + 20;
        graphPane.setPrefWidth(Math.max(900, maxX));
        graphPane.setPrefHeight(Math.max(600, maxY));

        return positions;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Dessin des arêtes (flèches entre les nœuds)
    // ══════════════════════════════════════════════════════════════════════

    private void drawEdges(List<Task> tasks, Map<String, double[]> positions) {
        Set<String> drawnIds = tasks.stream()
            .map(Task::getId).collect(Collectors.toSet());

        for (Task task : tasks) {
            double[] to = positions.get(task.getId());
            if (to == null) continue;

            for (Task dep : task.getDependencies()) {
                if (!drawnIds.contains(dep.getId())) continue;
                double[] from = positions.get(dep.getId());
                if (from == null) continue;

                // Ligne de dep → task
                Line line = new Line(from[0], from[1], to[0], to[1]);
                line.setStroke(COLOR_EDGE);
                line.setStrokeWidth(1.5);

                // Flèche au bout de la ligne
                Polygon arrow = buildArrow(from[0], from[1], to[0], to[1]);
                arrow.setFill(COLOR_EDGE);

                graphPane.getChildren().addAll(line, arrow);
            }
        }
    }

    /**
     * Construit une petite flèche triangulaire orientée vers (tx, ty).
     */
    private Polygon buildArrow(double fx, double fy, double tx, double ty) {
        double dx = tx - fx;
        double dy = ty - fy;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len == 0) return new Polygon();

        // Point d'arrivée sur le bord du cercle cible
        double ex = tx - (dx / len) * NODE_RADIUS;
        double ey = ty - (dy / len) * NODE_RADIUS;

        double angle = Math.atan2(dy, dx);
        double arrowLen = 10;
        double arrowAngle = Math.toRadians(25);

        double x1 = ex - arrowLen * Math.cos(angle - arrowAngle);
        double y1 = ey - arrowLen * Math.sin(angle - arrowAngle);
        double x2 = ex - arrowLen * Math.cos(angle + arrowAngle);
        double y2 = ey - arrowLen * Math.sin(angle + arrowAngle);

        return new Polygon(ex, ey, x1, y1, x2, y2);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Dessin des nœuds
    // ══════════════════════════════════════════════════════════════════════

    private void drawNodes(List<Task> tasks, Map<String, double[]> positions) {
        for (Task task : tasks) {
            double[] pos = positions.get(task.getId());
            if (pos == null) continue;

            double cx = pos[0];
            double cy = pos[1];
            Color statusColor = getStatusColor(task.getTaskStatus());

            // Ombre
            Circle shadow = new Circle(cx + 3, cy + 3, NODE_RADIUS);
            shadow.setFill(Color.rgb(0, 0, 0, 0.12));
            shadow.setMouseTransparent(true);

            // Cercle principal
            Circle circle = new Circle(cx, cy, NODE_RADIUS);
            circle.setFill(COLOR_NODE_BG);
            circle.setStroke(statusColor);
            circle.setStrokeWidth(2.5);

            // ID de la tâche (ex: T-001)
            Text idText = new Text(task.getId());
            idText.setFont(Font.font("Monospace", FontWeight.BOLD, 9));
            idText.setFill(statusColor);
            idText.setX(cx - idText.getLayoutBounds().getWidth() / 2);
            idText.setY(cy - 5);

            // Titre tronqué
            String shortTitle = task.getTitle().length() > 10
                ? task.getTitle().substring(0, 10) + "…"
                : task.getTitle();
            Text titleText = new Text(shortTitle);
            titleText.setFont(Font.font("System", 8));
            titleText.setFill(Color.web("#444444"));
            titleText.setX(cx - titleText.getLayoutBounds().getWidth() / 2);
            titleText.setY(cy + 8);

            // Clic sur le nœud → affiche le panneau d'info
            circle.setOnMouseClicked(e -> onNodeClicked(task));
            idText.setOnMouseClicked(e -> onNodeClicked(task));
            titleText.setOnMouseClicked(e -> onNodeClicked(task));

            // Survol : curseur pointeur
            circle.setStyle("-fx-cursor: hand;");

            graphPane.getChildren().addAll(shadow, circle, idText, titleText);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Clic sur un nœud
    // ══════════════════════════════════════════════════════════════════════

    private void onNodeClicked(Task task) {
        selectedTask = task;

        String assignee = task.getAssignedUser() != null
            ? task.getAssignedUser().getName()
            : "Non assigné";

        nodeInfoLabel.setText(
            task.getId() + " — " + task.getTitle() +
            "  |  " + task.getTaskStatus().name().replace("_", " ") +
            "  |  Priorité : " + task.getPriorityLevel().name() +
            "  |  Assigné : " + assignee
        );

        nodeInfoPanel.setVisible(true);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Bouton "Voir détail" du panneau d'info
    // ══════════════════════════════════════════════════════════════════════

    @FXML
    private void openTaskDetail() {
        if (selectedTask == null) return;

        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("../view/TaskDetailsView.fxml"));
            Parent root = loader.load();

            // Injection dans TaskDetailController
            Object ctrl = loader.getController();
            injectDependencies(ctrl);

            // Passer la tâche sélectionnée
            // Dans DependencyGraphController.java — méthode openTaskDetail()

            // Passer la tâche sélectionnée
            try {
                ctrl.getClass()
                    .getMethod("setTask", Task.class)
                    .invoke(ctrl, selectedTask);
            } catch (NoSuchMethodException | IllegalAccessException | 
                    java.lang.reflect.InvocationTargetException ignored) {}

            try {
                ctrl.getClass()
                    .getMethod("initialize")
                    .invoke(ctrl);
            } catch (NoSuchMethodException | IllegalAccessException | 
                    java.lang.reflect.InvocationTargetException ignored) {}

        } catch (IOException e) {
            nodeInfoLabel.setText("Erreur lors du chargement du détail : " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Utilitaires
    // ══════════════════════════════════════════════════════════════════════

    private Color getStatusColor(TaskStatus status) {
        return switch (status) {
            case TODO        -> COLOR_TODO;
            case BLOCKED     -> COLOR_BLOCKED;
            case IN_PROGRESS -> COLOR_IN_PROGRESS;
            case DONE        -> COLOR_DONE;
        };
    }

    private void injectDependencies(Object ctrl) {
        try {
            try {
                ctrl.getClass().getMethod("setTaskManager", TaskManager.class)
                    .invoke(ctrl, taskManager);
            } catch (NoSuchMethodException ignored) {}

            try {
                ctrl.getClass().getMethod("setCurrentUser", User.class)
                    .invoke(ctrl, currentUser);
            } catch (NoSuchMethodException ignored) {}

            try {
                ctrl.getClass().getMethod("setPasswords", Map.class)
                    .invoke(ctrl, passwords);
            } catch (NoSuchMethodException ignored) {}

        } catch (Exception e) {
            System.err.println("[DependencyGraphController] Injection échouée : "
                + e.getMessage());
        }
    }
}
