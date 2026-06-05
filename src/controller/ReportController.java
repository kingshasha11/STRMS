package src.controller;

import javafx.fxml.FXML;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import src.Task;
import src.TaskManager;
import src.User;
import src.enumeration.PriorityLevel;
import src.enumeration.TaskStatus;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ReportController {

    // ── Composants FXML ───────────────────────────────────────────────────
    @FXML private ToggleGroup reportTypeGroup;
    @FXML private TextArea    reportArea;

    // ── Dépendances injectées ──────────────────────────────────────────────
    private TaskManager taskManager;
    private User        currentUser;
    
    // Variable pour conserver le rapport brut sans les messages de succès/erreur d'export
    private String rawReportContent = "";

    public void setTaskManager(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    /**
     * Appelé automatiquement via l'arborescence UI après injection ou via les RadioButtons (onAction)
     */
    @FXML
    public void initialize() {
        // Sélectionner par défaut le premier type si aucun n'est sélectionné
        if (reportTypeGroup.getSelectedToggle() == null && !reportTypeGroup.getToggles().isEmpty()) {
            reportTypeGroup.getToggles().get(0).setSelected(true);
        }
        generateReport();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Génération dynamique des rapports textuels
    // ══════════════════════════════════════════════════════════════════════

    @FXML
    private void generateReport() {
        if (taskManager == null) {
            reportArea.setText(" [Erreur] Le gestionnaire de tâches n'est pas initialisé.");
            return;
        }

        Toggle selectedToggle = reportTypeGroup.getSelectedToggle();
        if (selectedToggle == null) {
            reportArea.setText(" Veuillez sélectionner un type de rapport.");
            return;
        }

        // Récupération de la valeur du userData défini dans le FXML (STATUS, USER, OVERDUE, PRIORITY)
        String type = (String) selectedToggle.getUserData();
        List<Task> allTasks = taskManager.getAllTasks();
        StringBuilder sb = new StringBuilder();

        switch (type) {
            case "STATUS" -> buildStatusReport(sb, allTasks);
            case "USER" -> buildUserReport(sb, allTasks);
            case "OVERDUE" -> buildOverdueReport(sb, allTasks);
            case "PRIORITY" -> buildPriorityReport(sb, allTasks);
            default -> sb.append(" Type de rapport inconnu ou non pris en charge.");
        }

        rawReportContent = sb.toString();
        reportArea.setText(rawReportContent);
    }

    // ── 1. Rapport par Statut ─────────────────────────────────────────────
    private void buildStatusReport(StringBuilder sb, List<Task> tasks) {
        sb.append(header("RAPPORT ANALYTIQUE : TÂCHES PAR STATUT"));

        Map<TaskStatus, List<Task>> grouped = tasks.stream()
                .collect(Collectors.groupingBy(Task::getTaskStatus));

        for (TaskStatus status : TaskStatus.values()) {
            List<Task> subList = grouped.getOrDefault(status, new ArrayList<>());
            sb.append(String.format("\n 📂 STATUT : %s (%d tâches)\n", status, subList.size()));
            sb.append("  ──────────────────────────────────────────────────────────────────────────────────────────\n");
            if (subList.isEmpty()) {
                sb.append("    (Aucune tâche dans cette section)\n");
            } else {
                subList.forEach(t -> sb.append(taskLine(t)));
            }
        }
        sb.append(footer(tasks.size()));
    }

    // ── 2. Rapport par Utilisateur Assigné ─────────────────────────────────
    private void buildUserReport(StringBuilder sb, List<Task> tasks) {
        sb.append(header("RAPPORT ANALYTIQUE : RÉPARTITION PAR UTILISATEUR"));

        // Grouper par nom de l'assigné (ou "Non assigné")
        Map<String, List<Task>> grouped = tasks.stream().collect(Collectors.groupingBy(t -> 
            t.getAssignedUser() != null ? t.getAssignedUser().getName() : "Non assigné"
        ));

        grouped.forEach((userName, subList) -> {
            sb.append(String.format("\n 👤 MEMBRE : %s (%d tâches)\n", userName, subList.size()));
            sb.append("  ──────────────────────────────────────────────────────────────────────────────────────────\n");
            subList.forEach(t -> sb.append(taskLine(t)));
        });

        if (tasks.isEmpty()) {
            sb.append("\n    Aucune tâche présente dans le système.\n");
        }
        sb.append(footer(tasks.size()));
    }

    // ── 3. Rapport des Tâches en Retard ────────────────────────────────────
    private void buildOverdueReport(StringBuilder sb, List<Task> tasks) {
        sb.append(header("RAPPORT DE CRISE : TÂCHES HORS ÉCHÉANCE (OVERDUE)"));

        Date now = new Date();
        List<Task> overdueTasks = tasks.stream()
                .filter(t -> t.getTaskStatus() != TaskStatus.DONE)
                .filter(t -> t.getDeadline() != null && t.getDeadline().before(now))
                .collect(Collectors.toList());

        sb.append(String.format("\n 🔥 %d tâches sont actuellement en retard sur leur deadline :\n", overdueTasks.size()));
        sb.append("  ──────────────────────────────────────────────────────────────────────────────────────────\n");

        if (overdueTasks.isEmpty()) {
            sb.append("    ✔ Merveilleux ! Aucune tâche non finalisée n'a dépassé son échéance.\n");
        } else {
            overdueTasks.forEach(t -> sb.append(taskLine(t)));
        }
        sb.append(footer(tasks.size()));
    }

    // ── 4. Rapport par Niveau de Priorité ─────────────────────────────────
    private void buildPriorityReport(StringBuilder sb, List<Task> tasks) {
        sb.append(header("RAPPORT OPÉRATIONNEL : PRIORITÉS DES TÂCHES"));

        Map<PriorityLevel, List<Task>> grouped = tasks.stream()
                .collect(Collectors.groupingBy(Task::getPriorityLevel));

        for (PriorityLevel level : PriorityLevel.values()) {
            List<Task> subList = grouped.getOrDefault(level, new ArrayList<>());
            sb.append(String.format("\n ⚡ PRIORITÉ : %s (%d tâches)\n", level, subList.size()));
            sb.append("  ──────────────────────────────────────────────────────────────────────────────────────────\n");
            if (subList.isEmpty()) {
                sb.append("    (Aucune tâche de ce niveau)\n");
            } else {
                subList.forEach(t -> sb.append(taskLine(t)));
            }
        }
        sb.append(footer(tasks.size()));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Action d'export physique vers un fichier .txt
    // ══════════════════════════════════════════════════════════════════════

    @FXML
    private void exportReport() {
        if (rawReportContent == null || rawReportContent.isBlank()) {
            reportArea.setText(" Aucun rapport généré à exporter.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Exporter le rapport opérationnel");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Fichiers texte (*.txt)", "*.txt")
        );
        fileChooser.setInitialFileName("Rapport_STRMS_" + System.currentTimeMillis() + ".txt");

        // Récupération du Stage courant pour ouvrir la boîte de dialogue modale
        Stage stage = (Stage) reportArea.getScene().getWindow();
        File file = fileChooser.showSaveDialog(stage);

        if (file != null) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                writer.write(rawReportContent);
                writer.flush();
                
                reportArea.setText(rawReportContent 
                    + "\n\n  ✔ Rapport exporté avec succès :\n  " + file.getAbsolutePath());
            } catch (IOException e) {
                reportArea.setText(rawReportContent 
                    + "\n\n  ✘ Erreur critique lors de l'export : " + e.getMessage());
            }
        }
    }

    // ── Helpers de formatage ───────────────────────────────────────────────

    private String header(String title) {
        return "  ==========================================================================================\n" +
               "   " + title + "\n" +
               "   Généré le : " + new Date() + "\n" +
               "   Par       : " + (currentUser != null ? currentUser.getName() : "Système") + "\n" +
               "  ==========================================================================================\n";
    }

    private String footer(int total) {
        return "\n  ──────────────────────────────────────────────────────────────────────────────────────────\n" +
               "   Total des tâches enregistrées dans le système : " + total + "\n" +
               "  ==========================================================================================\n";
    }

    private String taskLine(Task t) {
        User assignedUser = t.getAssignedUser();
        String assignee = (assignedUser != null) ? assignedUser.getName() + " (" + assignedUser.getRole() + ")" : "Non assigné";
        
        // Formatage clair en colonnes alignées : ID | Titre | Statut | Assigné
        return String.format("    - [%-5s] %-30s | %-12s | %-15s%n",
                t.getId(),
                t.getTitle().length() > 28 ? t.getTitle().substring(0, 25) + "..." : t.getTitle(),
                t.getTaskStatus(),
                assignee);
    }
}