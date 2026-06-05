package src.exceptions;

public class DuplicateTaskException extends Exception {
    public DuplicateTaskException(String taskId) {
        super("A task with ID '" + taskId + "' already exists in the system. Duplicate tasks are not allowed.");
    }
}
