package src.exceptions;

public class FilePersistenceException extends Exception {
    public FilePersistenceException(String filename, String reason) {
        super("File persistence error for '" + filename + "': " + reason);
    }
}
