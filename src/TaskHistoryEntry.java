package src;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Records a single auditable event in a task's lifecycle.
 * Instances are immutable once created.
 */
public class TaskHistoryEntry {

    private final String action;          // Description of what happened
    private final LocalDateTime timestamp; // When it happened
    private final User performedBy;       // Who triggered the event

    public TaskHistoryEntry(String action, User performedBy) {
        this.action = action;
        this.performedBy = performedBy;
        this.timestamp = LocalDateTime.now();
    }

    // -- Getters (read-only) --

    public String getAction() {
        return action;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public User getPerformedBy() {
        return performedBy;
    }

    @Override
    public String toString() {
        String formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                            .format(timestamp);
        String user = (performedBy != null) ? performedBy.getName() : "System";
        return "[" + formatter + "] " + user + " → " + action;
    }
}
