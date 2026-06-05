package src;

public class Engineer extends User {

    private String specialty;

    public Engineer(String id, String name, String email, String specialty) {
        super(id, name, email);
        this.specialty = specialty;
    }

    // Constructeur sans spécialité — pour UserManagerController
    public Engineer(String id, String name, String email) {
        super(id, name, email);
        this.specialty = "";
    }

    public String getSpecialty() { return specialty; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }

    @Override public boolean canCreateTask()     { return false; }
    @Override public boolean canDeleteTask()     { return false; }
    @Override public boolean canAssignTask()     { return false; }
    @Override public boolean canGenerateReport() { return false; }
    @Override public boolean canUpdateTask()     { return false; }
}