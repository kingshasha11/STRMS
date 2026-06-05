package src;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import src.enumeration.*;

public class Task implements Comparable<Task> {

    private String id;
    private String title;
    private String description;
    private PriorityLevel priorityLevel;
    private TaskStatus taskStatus;
    private TaskCategory taskCategory;
    private Date deadline;
    private User assignedUser;

    private final List<Task> dependencies = new ArrayList<>();
    private final List<TaskHistoryEntry> history = new ArrayList<>();

    public Task(String id, String title, String description,
                PriorityLevel priorityLevel, TaskStatus taskStatus,
                TaskCategory taskCategory, Date deadline) {
        this.id            = id;
        this.title         = title;
        this.description   = description;
        this.priorityLevel = priorityLevel;
        this.taskStatus    = taskStatus;
        this.taskCategory  = taskCategory;
        this.deadline      = deadline;
    }

    public void updateStatus(TaskStatus newStatus) { this.taskStatus = newStatus; }
    public void addHistoryEntry(TaskHistoryEntry entry) { history.add(entry); }

    public void addDependency(Task dependency) {
        if (dependency != null && !dependencies.contains(dependency))
            dependencies.add(dependency);
    }

    public void removeDependency(Task dependency) { dependencies.remove(dependency); }

    public boolean allDependenciesDone() {
        for (Task dep : dependencies)
            if (dep.getTaskStatus() != TaskStatus.DONE) return false;
        return true;
    }

    public void markAsDone()                            { this.taskStatus = TaskStatus.DONE; }
    public void changePriority(PriorityLevel p)         { this.priorityLevel = p; }
    public void updateDescription(String d)             { this.description = d; }
    public void assignUser(User user)                   { this.assignedUser = user; }

    // ── Aliases pour la compatibilité avec les contrôleurs ─────────────────
    /** Alias de getAssignedUser() — compatibilité contrôleurs */
    public Engineer getAssignedEngineer() {
        if (assignedUser instanceof Engineer) return (Engineer) assignedUser;
        return null;
    }

    public void displayTask() {
        System.out.println("Task: " + title + " [" + id + "] Status=" + taskStatus + " Priority=" + priorityLevel);
    }

    @Override
    public int compareTo(Task other) {
        return Integer.compare(this.priorityLevel.ordinal(), other.priorityLevel.ordinal());
    }

    // ── Getters ─────────────────────────────────────────────────────────────
    public String getId()                      { return id; }
    public String getTitle()                   { return title; }
    public String getDescription()             { return description; }
    public PriorityLevel getPriorityLevel()    { return priorityLevel; }
    public TaskStatus getTaskStatus()          { return taskStatus; }
    public TaskCategory getTaskCategory()      { return taskCategory; }
    public Date getDeadline()                  { return deadline; }
    public User getAssignedUser()              { return assignedUser; }
    public List<Task> getDependencies()        { return dependencies; }
    public List<TaskHistoryEntry> getHistory() { return history; }

    public void setId(String id)               { this.id = id; }
    public void setTitle(String title)         { this.title = title; }
    public void setDeadline(Date deadline)     { this.deadline = deadline; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Task)) return false;
        return Objects.equals(id, ((Task) o).id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "[" + id + "] " + title + " (" + taskStatus + " | " + priorityLevel + ")";
    }
}