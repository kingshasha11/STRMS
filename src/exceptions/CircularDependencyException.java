package src.exceptions;

public class CircularDependencyException extends Exception {
    public CircularDependencyException(String taskA, String taskB) {
        super("Circular dependency detected: adding dependency from Task '" + taskA +
              "' to Task '" + taskB + "' would create a cycle. Operation rejected.");
    }
}
