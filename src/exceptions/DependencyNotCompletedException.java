package src.exceptions;

public class DependencyNotCompletedException extends Exception{
     public DependencyNotCompletedException(String taskTitle, String dependencyTitle) {
        super("Cannot start task '" + taskTitle + "': dependency '" + dependencyTitle +
              "' is not yet completed. Task remains BLOCKED.");
    }

}
