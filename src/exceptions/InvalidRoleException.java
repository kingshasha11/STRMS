package src.exceptions;

public class InvalidRoleException extends Exception {
    public InvalidRoleException(String userName, String action) {
        super("User '" + userName + "' does not have the required role/permission to perform: " + action + ".");
    }
}
