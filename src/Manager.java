// Manager.java
package src;

public class Manager extends User {

    private final String team;

    public Manager(String id, String name, String email, String team) {
        super(id, name, email);
        this.team = team;
    }

    public String getTeam() { return team; }

    @Override public boolean canCreateTask()     { return false; }
    @Override public boolean canDeleteTask()     { return false; }
    @Override public boolean canAssignTask()     { return true;  }
    @Override public boolean canGenerateReport() { return true;  }
    @Override public boolean canUpdateTask()     { return true;  }
}