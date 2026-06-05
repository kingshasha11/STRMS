package src;

import java.util.Objects;

public abstract class User {

    protected String id;
    protected String name;
    protected String email;

    public User(String id, String name, String email) {
        if (id    == null || id.isBlank())    throw new IllegalArgumentException("L'ID ne peut pas être vide.");
        if (name  == null || name.isBlank())  throw new IllegalArgumentException("Le nom ne peut pas être vide.");
        if (email == null || email.isBlank()) throw new IllegalArgumentException("L'email ne peut pas être vide.");

        this.id    = id;
        this.name  = name;
        this.email = email;
    }

    public abstract boolean canCreateTask();
    public abstract boolean canDeleteTask();
    public abstract boolean canAssignTask();
    public abstract boolean canGenerateReport();
    public abstract boolean canUpdateTask();

    public String getRole()  { return getClass().getSimpleName(); }
    public String getId()    { return id;    }
    public String getName()  { return name;  }
    public String getEmail() { return email; }

    public void setName(String name) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Le nom ne peut pas être vide.");
        this.name = name;
    }

    public void setEmail(String email) {
        if (email == null || email.isBlank())
            throw new IllegalArgumentException("L'email ne peut pas être vide.");
        if (!email.contains("@"))
            throw new IllegalArgumentException("Format d'email invalide.");
        this.email = email;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        return Objects.equals(this.id, ((User) o).id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return getRole() + "[" + id + "] " + name + " <" + email + ">";
    }
}