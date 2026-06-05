package src;

public class Admin extends User {

    private final int accessLevel;

    public Admin(String id, String name, String email) {
        super(id, name, email);
        this.accessLevel = 1;
    }

    public Admin(String id, String name, String email, int accessLevel) {
        super(id, name, email);
        this.accessLevel = accessLevel;
    }

    // Constructeur sans spécialité (équivalent Engineer/Manager) — pour UserManagerController
    public static Admin create(String id, String name, String email) {
        return new Admin(id, name, email, 1);
    }

    public int getAccessLevel() { return accessLevel; }

    @Override public boolean canCreateTask()     { return true; }
    @Override public boolean canDeleteTask()     { return true; }
    @Override public boolean canAssignTask()     { return true; }
    @Override public boolean canGenerateReport() { return true; }
    @Override public boolean canUpdateTask()     { return true; }
}