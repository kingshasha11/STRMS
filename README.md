# STRMS - Smart Task & Resource Management System


## Aperçu

**STRMS** est une application de bureau développée en **Java 21 avec JavaFX**, qui simule un système de gestion de tâches dans un contexte d'ingénierie. Elle implémente les quatre piliers de la **programmation orientée objet** et met en œuvre des structures de données avancées, un système de dépendances entre tâches avec détection de cycles, et une persistance de données par fichiers CSV.

---

## Fonctionnalités

- **Authentification** par rôle - Admin, Manager, Engineer
- **Gestion complète des tâches** - création, assignation, mise à jour, suppression
- **Dépendances entre tâches** - graphe DAG avec détection de dépendances circulaires (DFS)
- **Historique d'audit** - chaque action est tracée avec auteur et horodatage
- **File de priorité** - les tâches sont ordonnancées par niveau (CRITICAL > HIGH > MEDIUM > LOW)
- **Graphe de dépendances** - visualisation graphique des relations entre tâches
- **Rapports exportables** - par statut, par utilisateur, tâches en retard, par priorité
- **Gestion des utilisateurs** - CRUD avec contrôle d'accès par rôle
- **Persistance CSV automatique** - sauvegarde après chaque modification (tâches, dépendances, historique)

---

## Stack technique

| Technologie | Usage |
|---|---|
| Java 21 | Langage principal |
| JavaFX 21 | Interface graphique (FXML + contrôleurs) |
| CSV | Persistance des données |
| Git | Gestion de version |


## Structures de données

| Structure | Utilisation |
|---|---|
| `HashMap<String, Task>` | Accès O(1) aux tâches par ID |
| `HashMap<String, User>` | Accès O(1) aux utilisateurs par ID |
| `HashSet<Task>` | Suivi des tâches IN_PROGRESS sans doublons |
| `PriorityQueue<Task>` | File d'attente ordonnée par priorité |
| `ArrayList<TaskHistoryEntry>` | Historique chronologique par tâche |

---

## Cycle de vie d'une tâche

```
TODO ──► BLOCKED ──► IN_PROGRESS ──► DONE
               ▲         │
               └─────────┘ (si dépendance non satisfaite)
```

`DONE` est un état terminal — aucun retour en arrière possible.

---

## Prérequis

- **JDK 21** - [Télécharger ici](https://www.oracle.com/java/technologies/downloads/#java21)
- **JavaFX 21** - [Télécharger ici](https://gluonhq.com/products/javafx/)
- **VS Code** avec l'extension *Extension Pack for Java* (recommandé)

---

## Installation et lancement

### 1. Cloner le dépôt

```bash
git clone https://github.com/kingshasha11/STRMS.git
cd STRMS
```

### 2. Configurer JavaFX

Dans `.vscode/settings.json`, vérifier que le chemin vers JavaFX est correct :

```json
{
  "java.project.referencedLibraries": [
    "/chemin-vers-javafx-21/lib/**"
  ]
}
```

### 3. Lancer l'application

Dans `.vscode/launch.json`, vérifier que le chemin vers JavaFX est correct :

```json
{
  "version": "0.2.0",
    "configurations": [
        {
            "type": "java",
            "name": "Lancer JavaFX",
            "request": "launch",
            "mainClass": "src.Main",
            "vmArgs": "--module-path \"C:/votre-chemin-vers-javafx-21/lib\" --add-modules javafx.controls,javafx.fxml --add-opens javafx.graphics/com.sun.javafx.util=ALL-UNNAMED"
        }
    ]
}
```

---

## Comptes de test

| Rôle | Email | Mot de passe | Permissions |
|---|---|---|---|
| Admin | alice@strms.fr | admin123 | Tout — créer, supprimer, assigner, rapports |
| Manager | bob@strms.fr | manager123 | Assigner, rapports, modifier |
| Engineer | charlie@strms.fr | engineer123 | Voir, démarrer, terminer ses tâches |


## Interfaces graphiques

| Interface | Rôles | Description |
|---|---|---|
| LoginView | Tous | Authentification par email / mot de passe |
| DashboardView | Tous | Statistiques globales et navigation |
| TaskListView | Tous | Liste filtrée des tâches avec recherche |
| TaskFormView | Admin | Création et modification de tâches |
| TaskDetailsView | Tous | Détail, historique et actions sur une tâche |
| DependencyGraphView | Tous | Visualisation du graphe DAG |
| ReportView | Admin, Manager | Génération et export de rapports (.txt) |
| UserManagerView | Admin | CRUD des utilisateurs du système |

---

## Persistance des données

La sauvegarde est **automatique** après chaque opération (ajout, modification, suppression, assignation). Trois fichiers CSV sont maintenus dans `src/data/` :

- `tasks.csv` - données principales des tâches
- `dependencies.csv` - relations de dépendance entre tâches
- `history.csv` - journal d'audit complet


## Auteur

**Louis-David MVEMBA**  
**Data & IA Student** 
