package src.exceptions;

public class InvalidTaskStateException extends Exception {
    public InvalidTaskStateException(String taskTitle, String fromState, String toState) {
        super("Invalid state transition for task '" + taskTitle + "': cannot transition from " +
              fromState + " to " + toState + ".");
    }
}
