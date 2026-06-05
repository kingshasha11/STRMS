package src.exceptions;

public class TaskNotFoundException extends Exception {
    public TaskNotFoundException(String taskId) {
        super("Task with ID '" + taskId + "' was not found in the system.");
    }
}
