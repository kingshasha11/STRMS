package src;

import src.enumeration.PriorityLevel;
import src.enumeration.TaskCategory;
import src.enumeration.TaskStatus;
import src.exceptions.*;

import java.io.*;
import java.util.*;

public class TaskManager {

    private final HashMap<String, Task> tasks         = new HashMap<>();
    private final HashMap<String, User> users         = new HashMap<>();
    private final HashSet<Task>         inProgressTasks = new HashSet<>();
    private final PriorityQueue<Task>   taskQueue     = new PriorityQueue<>();

    // ── User management ──────────────────────────────────────────────────────

    public void addUser(User user) {
        if (user != null) users.put(user.getId(), user);
    }

    public void addUser(User user, User requestedBy) throws InvalidRoleException {
        if (!(requestedBy instanceof Admin))
            throw new InvalidRoleException(requestedBy.getName(), "add a user");
        addUser(user);
    }

    public User findUser(String userId) throws TaskNotFoundException {
        User user = users.get(userId);
        if (user == null) throw new TaskNotFoundException("User:" + userId);
        return user;
    }

    public void deleteUser(String id, User requestedBy)
            throws InvalidRoleException, TaskNotFoundException {
        if (!(requestedBy instanceof Admin))
            throw new InvalidRoleException(requestedBy.getName(), "delete a user");
        if (!users.containsKey(id))
            throw new TaskNotFoundException("User ID: " + id);

        for (Task t : tasks.values()) {
            if (t.getAssignedUser() != null && t.getAssignedUser().getId().equals(id)) {
                t.assignUser(null);
                if (t.getTaskStatus() == TaskStatus.IN_PROGRESS) {
                    t.updateStatus(TaskStatus.TODO);
                    inProgressTasks.remove(t);
                    taskQueue.offer(t);
                }
                t.addHistoryEntry(new TaskHistoryEntry(
                    "Unassigned automatically because the user was deleted.", requestedBy));
            }
        }
        users.remove(id);
        System.out.println("[TaskManager] User '" + id + "' deleted by " + requestedBy.getName());
    }

    public HashMap<String, User> getUsers() { return users; }

    // ── Task CRUD ────────────────────────────────────────────────────────────

    public void addTask(Task task, User requestedBy)
            throws InvalidRoleException, DuplicateTaskException {
        if (!requestedBy.canCreateTask())
            throw new InvalidRoleException(requestedBy.getName(), "create a task");
        if (tasks.containsKey(task.getId()))
            throw new DuplicateTaskException(task.getId());

        tasks.put(task.getId(), task);

        if (task.allDependenciesDone()) {
            taskQueue.offer(task);
        } else {
            task.updateStatus(TaskStatus.BLOCKED);
        }

        task.addHistoryEntry(new TaskHistoryEntry(
            "Task created with status " + task.getTaskStatus(), requestedBy));

        // ── Auto-sauvegarde après chaque modification ──────────────────────
        autoSave();
    }

    public void deleteTask(String taskId, User requestedBy)
            throws InvalidRoleException, TaskNotFoundException {
        if (!requestedBy.canDeleteTask())
            throw new InvalidRoleException(requestedBy.getName(), "delete a task");
        Task task = findTask(taskId);

        tasks.remove(taskId);
        inProgressTasks.remove(task);
        taskQueue.remove(task);

        autoSave();
        System.out.println("[TaskManager] Task '" + task.getTitle() + "' deleted by " + requestedBy.getName());
    }

    public void assignTask(String taskId, User engineerOrManager, User requestedBy)
            throws InvalidRoleException, TaskNotFoundException,
                   DependencyNotCompletedException, InvalidTaskStateException {
        if (!requestedBy.canAssignTask())
            throw new InvalidRoleException(requestedBy.getName(), "assign a task");
        if (engineerOrManager instanceof Admin)
            throw new InvalidRoleException(engineerOrManager.getName(),
                "be assigned to an operational task (Admin restricted)");

        Task task = findTask(taskId);

        if (task.getTaskStatus() == TaskStatus.DONE)
            throw new InvalidTaskStateException(task.getTitle(), "DONE", "IN_PROGRESS");

        for (Task dep : task.getDependencies()) {
            if (dep.getTaskStatus() != TaskStatus.DONE) {
                task.addHistoryEntry(new TaskHistoryEntry(
                    "BLOCKED: Attempted assignment failed — dependency '"
                    + dep.getTitle() + "' is not completed.", requestedBy));
                throw new DependencyNotCompletedException(task.getTitle(), dep.getTitle());
            }
        }

        task.assignUser(engineerOrManager);
        task.updateStatus(TaskStatus.IN_PROGRESS);
        inProgressTasks.add(task);
        taskQueue.remove(task);

        task.addHistoryEntry(new TaskHistoryEntry(
            "Assigned to " + engineerOrManager.getRole() + " '" + engineerOrManager.getName()
            + "' by " + requestedBy.getName() + ". Status → IN_PROGRESS", requestedBy));

        autoSave();
    }

    public void completeTask(String taskId, User requestedBy)
            throws TaskNotFoundException, InvalidRoleException, InvalidTaskStateException {
        Task task = findTask(taskId);
        User assigned = task.getAssignedUser();

        if (assigned == null || (!assigned.getId().equals(requestedBy.getId())
                && !requestedBy.canUpdateTask()))
            throw new InvalidRoleException(requestedBy.getName(),
                "complete task '" + task.getTitle() + "' (not the assigned operator)");

        if (task.getTaskStatus() == TaskStatus.DONE)
            throw new InvalidTaskStateException(task.getTitle(), "DONE", "DONE");

        task.markAsDone();
        inProgressTasks.remove(task);

        task.addHistoryEntry(new TaskHistoryEntry(
            "Task marked as DONE by " + requestedBy.getName(), requestedBy));

        activateDependentTasks(task);
        autoSave();
    }

    public void updateTask(String taskId, TaskStatus newStatus, User requestedBy)
            throws TaskNotFoundException, InvalidTaskStateException,
                   InvalidRoleException, DependencyNotCompletedException {
        Task task = findTask(taskId);
        TaskStatus current = task.getTaskStatus();

        if (current == TaskStatus.DONE)
            throw new InvalidTaskStateException(task.getTitle(), "DONE", newStatus.name());

        if (newStatus == TaskStatus.IN_PROGRESS) {
            for (Task dep : task.getDependencies()) {
                if (dep.getTaskStatus() != TaskStatus.DONE) {
                    task.addHistoryEntry(new TaskHistoryEntry(
                        "BLOCKED: Status change to IN_PROGRESS refused — dependency '"
                        + dep.getTitle() + "' incomplete.", requestedBy));
                    throw new DependencyNotCompletedException(task.getTitle(), dep.getTitle());
                }
            }
            inProgressTasks.add(task);
        }

        task.updateStatus(newStatus);
        task.addHistoryEntry(new TaskHistoryEntry(
            "Status changed from " + current + " to " + newStatus
            + " by " + requestedBy.getName(), requestedBy));

        autoSave();
    }

    // ── Dependencies ─────────────────────────────────────────────────────────

    public void addDependency(String taskId, String dependsOnId, User requestedBy)
            throws TaskNotFoundException, CircularDependencyException,
                   InvalidTaskStateException, InvalidRoleException {
        Task task      = findTask(taskId);
        Task dependsOn = findTask(dependsOnId);

        if (taskId.equals(dependsOnId))
            throw new CircularDependencyException(taskId, dependsOnId);

        if (task.getTaskStatus() == TaskStatus.DONE)
            throw new InvalidTaskStateException(task.getTitle(), "DONE", "adding dependency");

        if (detectCircularDependency(dependsOn, task)) {
            task.addHistoryEntry(new TaskHistoryEntry(
                "REJECTED: Adding dependency on '" + dependsOn.getTitle()
                + "' would create a circular dependency.", requestedBy));
            throw new CircularDependencyException(task.getTitle(), dependsOn.getTitle());
        }

        task.addDependency(dependsOn);

        if (dependsOn.getTaskStatus() != TaskStatus.DONE
                && task.getTaskStatus() != TaskStatus.IN_PROGRESS) {
            task.updateStatus(TaskStatus.BLOCKED);
            taskQueue.remove(task);
        }

        task.addHistoryEntry(new TaskHistoryEntry(
            "Dependency added: task now depends on '"
            + dependsOn.getTitle() + "'. Added by " + requestedBy.getName(), requestedBy));

        autoSave();
    }

    public void removeDependency(String taskId, String dependsOnId, User requestedBy)
            throws TaskNotFoundException {
        Task task      = findTask(taskId);
        Task dependsOn = findTask(dependsOnId);

        task.removeDependency(dependsOn);

        if (task.allDependenciesDone() && task.getTaskStatus() == TaskStatus.BLOCKED) {
            task.updateStatus(TaskStatus.TODO);
            taskQueue.offer(task);
        }

        task.addHistoryEntry(new TaskHistoryEntry(
            "Dependency on '" + dependsOn.getTitle() + "' removed by "
            + requestedBy.getName(), requestedBy));

        autoSave();
    }

    public boolean detectCircularDependency(Task current, Task target) {
        if (current == target) return true;
        Set<String> visited = new HashSet<>();
        return dfs(current, target, visited);
    }

    private boolean dfs(Task node, Task target, Set<String> visited) {
        if (node.getId().equals(target.getId())) return true;
        if (visited.contains(node.getId())) return false;
        visited.add(node.getId());
        for (Task dep : node.getDependencies())
            if (dfs(dep, target, visited)) return true;
        return false;
    }

    // ── Lookup ───────────────────────────────────────────────────────────────

    public Task findTask(String taskId) throws TaskNotFoundException {
        Task task = tasks.get(taskId);
        if (task == null) throw new TaskNotFoundException(taskId);
        return task;
    }

    public HashMap<String, Task>    getTasks()           { return tasks; }
    public HashSet<Task>            getInProgressTasks() { return inProgressTasks; }
    public PriorityQueue<Task>      getTaskQueue()       { return taskQueue; }

    // ── getAllTasks convenience ───────────────────────────────────────────────
    public List<Task> getAllTasks() { return new ArrayList<>(tasks.values()); }

    // ── Dashboard console ─────────────────────────────────────────────────────

    public void displayDashboard() {
        System.out.println("\n╔══════════════════════════════════════╗");
        System.out.println("║         STRMS  DASHBOARD             ║");
        System.out.println("╠══════════════════════════════════════╣");
        System.out.printf ("║ Total tasks    : %-20s║%n", tasks.size());
        System.out.printf ("║ TODO           : %-20s║%n", count(TaskStatus.TODO));
        System.out.printf ("║ BLOCKED        : %-20s║%n", count(TaskStatus.BLOCKED));
        System.out.printf ("║ IN_PROGRESS    : %-20s║%n", count(TaskStatus.IN_PROGRESS));
        System.out.printf ("║ DONE           : %-20s║%n", count(TaskStatus.DONE));
        System.out.println("╚══════════════════════════════════════╝\n");
    }

    private long count(TaskStatus s) {
        return tasks.values().stream().filter(t -> t.getTaskStatus() == s).count();
    }

    // ── Persistence ──────────────────────────────────────────────────────────

    private static final String DATA_FILE     = "src/data/tasks.csv";
    private static final String DEP_FILE      = "src/data/dependencies.csv";
    private static final String HISTORY_FILE  = "src/data/history.csv";

    /**
     * Sauvegarde automatique appelée après chaque mutation.
     * Silencieuse — ne bloque jamais l'opération principale.
     */
    private void autoSave() {
        try {
            saveTasksToFile(DATA_FILE);
            saveDependenciesToFile(DEP_FILE);
            saveHistoryToFile(HISTORY_FILE);
        } catch (FilePersistenceException e) {
            System.err.println("[TaskManager] Auto-save warning: " + e.getMessage());
        }
    }

    /**
     * Chargement complet : tâches + dépendances + historique.
     * Appelé une seule fois au démarrage depuis Main.
     */
    public void loadAll() {
        try { loadTasksFromFile(DATA_FILE); }
        catch (FilePersistenceException e) {
            System.out.println("[Main] Aucune sauvegarde de tâches trouvée.");
        }
        try { loadDependenciesFromFile(DEP_FILE); }
        catch (FilePersistenceException e) {
            System.out.println("[Main] Aucune sauvegarde de dépendances trouvée.");
        }
        try { loadHistoryFromFile(HISTORY_FILE); }
        catch (FilePersistenceException e) {
            System.out.println("[Main] Aucune sauvegarde d'historique trouvée.");
        }
    }

    // ── tasks.csv ─────────────────────────────────────────────────────────────

    public void saveTasksToFile(String filename) throws FilePersistenceException {
        ensureParentDirs(filename);
        try (BufferedWriter w = new BufferedWriter(new FileWriter(filename))) {
            w.write("id,title,description,priority,status,category,deadline,assignedUserId");
            w.newLine();
            for (Task t : tasks.values()) {
                String uid      = t.getAssignedUser() != null ? t.getAssignedUser().getId() : "";
                String deadline = t.getDeadline()     != null ? String.valueOf(t.getDeadline().getTime()) : "";
                // Échapper les virgules dans les champs texte
                w.write(String.join(",",
                    escape(t.getId()), escape(t.getTitle()), escape(t.getDescription()),
                    t.getPriorityLevel().name(), t.getTaskStatus().name(),
                    t.getTaskCategory().name(), deadline, uid));
                w.newLine();
            }
        } catch (IOException e) {
            throw new FilePersistenceException(filename, e.getMessage());
        }
    }

    public void loadTasksFromFile(String filename) throws FilePersistenceException {
        try (BufferedReader r = new BufferedReader(new FileReader(filename))) {
            String line = r.readLine(); // header
            while ((line = r.readLine()) != null) {
                String[] p = splitCsv(line);
                if (p.length < 8) continue;

                Date deadline = p[6].isEmpty() ? null : new Date(Long.parseLong(p[6]));

                Task task = new Task(
                    p[0], p[1], p[2],
                    PriorityLevel.valueOf(p[3]),
                    TaskStatus.valueOf(p[4]),
                    TaskCategory.valueOf(p[5]),
                    deadline
                );

                if (!p[7].isEmpty()) {
                    User u = users.get(p[7]);
                    if (u != null) task.assignUser(u);
                }

                tasks.put(task.getId(), task);

                if (task.getTaskStatus() == TaskStatus.IN_PROGRESS)
                    inProgressTasks.add(task);
                else if (task.getTaskStatus() == TaskStatus.TODO)
                    taskQueue.offer(task);
            }
            System.out.println("[TaskManager] Tasks loaded from '" + filename + "'");
        } catch (IOException | IllegalArgumentException e) {
            throw new FilePersistenceException(filename, e.getMessage());
        }
    }

    // ── dependencies.csv ──────────────────────────────────────────────────────

    public void saveDependenciesToFile(String filename) throws FilePersistenceException {
        ensureParentDirs(filename);
        try (BufferedWriter w = new BufferedWriter(new FileWriter(filename))) {
            w.write("taskId,dependsOnId");
            w.newLine();
            for (Task t : tasks.values()) {
                for (Task dep : t.getDependencies()) {
                    w.write(t.getId() + "," + dep.getId());
                    w.newLine();
                }
            }
        } catch (IOException e) {
            throw new FilePersistenceException(filename, e.getMessage());
        }
    }

    public void loadDependenciesFromFile(String filename) throws FilePersistenceException {
        try (BufferedReader r = new BufferedReader(new FileReader(filename))) {
            String line = r.readLine(); // header
            while ((line = r.readLine()) != null) {
                String[] p = line.split(",");
                if (p.length < 2) continue;
                Task task = tasks.get(p[0].trim());
                Task dep  = tasks.get(p[1].trim());
                if (task != null && dep != null) {
                    task.addDependency(dep);
                    // Remettre à BLOCKED si la dépendance n'est pas DONE
                    if (dep.getTaskStatus() != TaskStatus.DONE
                            && task.getTaskStatus() == TaskStatus.TODO) {
                        task.updateStatus(TaskStatus.BLOCKED);
                        taskQueue.remove(task);
                    }
                }
            }
            System.out.println("[TaskManager] Dependencies loaded from '" + filename + "'");
        } catch (IOException e) {
            throw new FilePersistenceException(filename, e.getMessage());
        }
    }

    // ── history.csv ───────────────────────────────────────────────────────────

    public void saveHistoryToFile(String filename) throws FilePersistenceException {
        ensureParentDirs(filename);
        try (BufferedWriter w = new BufferedWriter(new FileWriter(filename))) {
            w.write("taskId,timestamp,userId,action");
            w.newLine();
            for (Task t : tasks.values()) {
                for (TaskHistoryEntry e : t.getHistory()) {
                    String uid = e.getPerformedBy() != null ? e.getPerformedBy().getId() : "";
                    w.write(String.join(",",
                        escape(t.getId()),
                        escape(e.getTimestamp() != null ? e.getTimestamp().toString() : ""),
                        escape(uid),
                        escape(e.getAction())));
                    w.newLine();
                }
            }
        } catch (IOException e) {
            throw new FilePersistenceException(filename, e.getMessage());
        }
    }

    public void loadHistoryFromFile(String filename) throws FilePersistenceException {
        try (BufferedReader r = new BufferedReader(new FileReader(filename))) {
            String line = r.readLine(); // header
            while ((line = r.readLine()) != null) {
                String[] p = splitCsv(line);
                if (p.length < 4) continue;
                Task task = tasks.get(p[0]);
                if (task == null) continue;
                User performer = users.get(p[2]);
                // Recréer une entrée d'historique avec l'action seulement
                // (le timestamp sera "now" mais le texte préserve l'info originale)
                task.addHistoryEntry(new TaskHistoryEntry("[Restored] " + p[3], performer));
            }
            System.out.println("[TaskManager] History loaded from '" + filename + "'");
        } catch (IOException e) {
            throw new FilePersistenceException(filename, e.getMessage());
        }
    }

    // ── Report ────────────────────────────────────────────────────────────────

    public void generateReport(String filename) throws FilePersistenceException {
        ensureParentDirs(filename);
        try (BufferedWriter w = new BufferedWriter(new FileWriter(filename))) {
            w.write("=== STRMS Task Report ===\n\n");
            for (Task t : tasks.values()) {
                w.write("Task: " + t.getTitle() + " [" + t.getId() + "]\n");
                w.write("  Status   : " + t.getTaskStatus() + "\n");
                w.write("  Priority : " + t.getPriorityLevel() + "\n");
                w.write("  Category : " + t.getTaskCategory() + "\n");
                w.write("  Assigned : " + (t.getAssignedUser() != null
                    ? t.getAssignedUser().getName() + " (" + t.getAssignedUser().getRole() + ")"
                    : "Unassigned") + "\n");
                w.write("  History  :\n");
                for (TaskHistoryEntry e : t.getHistory())
                    w.write("    " + e.toString() + "\n");
                w.write("\n");
            }
        } catch (IOException e) {
            throw new FilePersistenceException(filename, e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void activateDependentTasks(Task completedTask) {
        for (Task t : tasks.values()) {
            if (t.getTaskStatus() == TaskStatus.BLOCKED && t.allDependenciesDone()) {
                t.updateStatus(TaskStatus.TODO);
                taskQueue.offer(t);
                t.addHistoryEntry(new TaskHistoryEntry(
                    "Automatically unblocked: all dependencies are now DONE.", null));
                System.out.println("[TaskManager] Task '" + t.getTitle() + "' unblocked.");
            }
        }
    }

    private void ensureParentDirs(String filename) {
        File f = new File(filename).getParentFile();
        if (f != null && !f.exists()) f.mkdirs();
    }

    /** Échappe un champ CSV (remplace les virgules internes par un espace). */
    private String escape(String s) {
        if (s == null) return "";
        return s.replace(",", " ").replace("\n", " ").replace("\r", "");
    }

    // ── Méthodes additionnelles pour UserManagerController ────────────────────

    /** Retourne tous les utilisateurs sous forme de List. */
    public List<User> getAllUsers() {
        return new ArrayList<>(users.values());
    }

    /** Compte les tâches assignées à un utilisateur par son ID. */
    public int getUserAssignedTasksCount(String userId) {
        return (int) tasks.values().stream()
            .filter(t -> t.getAssignedUser() != null
                    && t.getAssignedUser().getId().equals(userId))
            .count();
    }

    /** Split simple respectant les espaces à la place des virgules. */
    private String[] splitCsv(String line) {
        return line.split(",", -1);
    }

    public void printInProgressTasks() {
        System.out.println("\n── In-Progress Tasks ──────────────────");
        inProgressTasks.forEach(t -> System.out.println("  " + t));
        System.out.println("──────────────────────────────────────\n");
    }

    public void printAllTasks() {
        System.out.println("\n── All Tasks ──────────────────────────");
        tasks.values().forEach(t -> System.out.println("  " + t));
        System.out.println("──────────────────────────────────────\n");
    }
}