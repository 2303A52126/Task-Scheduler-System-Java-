package scheduler;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.InputMismatchException;
import java.util.Scanner;

/**
 * Main — Console-based menu-driven interface for the Task Scheduler.
 *
 * Run: javac -d out src/main/java/scheduler/*.java && java -cp out scheduler.Main
 */
public class Main {

    private static final Scanner sc        = new Scanner(System.in);
    private static final TaskScheduler scheduler = new TaskScheduler();

    // ANSI colour codes (ignored on Windows cmd; works on most modern terminals)
    private static final String RESET  = "\u001B[0m";
    private static final String BOLD   = "\u001B[1m";
    private static final String CYAN   = "\u001B[36m";
    private static final String GREEN  = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED    = "\u001B[31m";

    public static void main(String[] args) {
        printBanner();
        seedDemoTasks();   // pre-load a few demo tasks so the user can explore immediately

        boolean running = true;
        while (running) {
            printMenu();
            int choice = readInt("  Enter choice: ");
            System.out.println();
            switch (choice) {
                case 1  -> addTaskFlow();
                case 2  -> scheduler.viewAll();
                case 3  -> executeNextFlow();
                case 4  -> deleteTaskFlow();
                case 5  -> scheduler.displayExecutionOrder();
                case 6  -> addDependencyFlow();
                case 7  -> scheduler.viewExecutionLog();
                case 8  -> scheduler.executeAll();
                case 9  -> printComplexityGuide();
                case 0  -> { running = false; printGoodbye(); }
                default -> System.out.println(RED + "  Invalid option. Try again." + RESET);
            }
            System.out.println();
        }
        sc.close();
    }

    // ── Menu Flows ────────────────────────────────────────────────────────────

    /** Guided flow to create and add a new task. */
    private static void addTaskFlow() {
        System.out.println(CYAN + BOLD + "  ── Add New Task ──" + RESET);

        System.out.print("  Task name: ");
        String name = sc.nextLine().trim();
        if (name.isBlank()) {
            System.out.println(RED + "  Name cannot be empty." + RESET);
            return;
        }

        System.out.println("  Priority:  1=HIGH  2=MEDIUM  3=LOW");
        int priVal = readInt("  Choice: ");
        Task.Priority priority;
        try {
            priority = Task.Priority.fromValue(priVal);
        } catch (IllegalArgumentException e) {
            System.out.println(RED + "  " + e.getMessage() + RESET);
            return;
        }

        System.out.print("  Scheduled time? (yyyy-MM-dd HH:mm) [Enter to skip]: ");
        String timeStr = sc.nextLine().trim();
        LocalDateTime scheduledTime = null;
        if (!timeStr.isBlank()) {
            try {
                scheduledTime = LocalDateTime.parse(timeStr,
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            } catch (DateTimeParseException e) {
                System.out.println(YELLOW + "  Invalid date format — scheduled time ignored." + RESET);
            }
        }

        Task task = new Task(name, priority, scheduledTime);
        scheduler.addTask(task);
    }

    /** Execute the next highest-priority task and display result. */
    private static void executeNextFlow() {
        System.out.println(CYAN + BOLD + "  ── Execute Next Task ──" + RESET);
        scheduler.executeNext().ifPresent(t -> {
            System.out.println(GREEN + "\n  ✔  Executed: " + t.toShortString() + RESET);
            System.out.printf("     Priority : %s%n", t.getPriority().name());
            if (t.getScheduledTime() != null) {
                System.out.printf("     Scheduled: %s%n",
                    t.getScheduledTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            }
            System.out.printf("     Remaining: %d task(s) in queue%n", scheduler.size());
        });
    }

    /** Guided flow to delete a task by ID. */
    private static void deleteTaskFlow() {
        System.out.println(CYAN + BOLD + "  ── Delete Task ──" + RESET);
        scheduler.viewAll();
        if (scheduler.isEmpty()) return;
        System.out.print("\n  Enter Task ID to delete: ");
        String id = sc.nextLine().trim();
        scheduler.deleteTask(id);
    }

    /** Guided flow to add a dependency between two tasks. */
    private static void addDependencyFlow() {
        System.out.println(CYAN + BOLD + "  ── Add Dependency ──" + RESET);
        System.out.println("  (Task A will NOT execute until Task B is done)");
        scheduler.viewAll();
        if (scheduler.isEmpty()) return;

        System.out.print("\n  Task A ID (the dependent task): ");
        String taskId = sc.nextLine().trim();
        System.out.print("  Task B ID (must be executed first): ");
        String depId  = sc.nextLine().trim();
        scheduler.addDependency(taskId, depId);
    }

    // ── Display Helpers ───────────────────────────────────────────────────────

    private static void printBanner() {
        System.out.println();
        System.out.println(CYAN + BOLD + "  ╔══════════════════════════════════════════════╗");
        System.out.println("  ║        TASK SCHEDULER SYSTEM  v1.0           ║");
        System.out.println("  ║   Priority Queue · Graph Dependencies · OOP  ║");
        System.out.println("  ╚══════════════════════════════════════════════╝" + RESET);
        System.out.println();
    }

    private static void printMenu() {
        System.out.println(BOLD + "  ┌─────────────────────────────────┐");
        System.out.println("  │           MAIN MENU             │");
        System.out.println("  ├─────────────────────────────────┤");
        System.out.println("  │  1. Add Task                    │");
        System.out.println("  │  2. View All Tasks              │");
        System.out.println("  │  3. Execute Next Task           │");
        System.out.println("  │  4. Delete Task                 │");
        System.out.println("  │  5. Display Execution Order     │");
        System.out.println("  │  6. Add Dependency              │");
        System.out.println("  │  7. View Execution Log          │");
        System.out.println("  │  8. Execute All Tasks           │");
        System.out.println("  │  9. Time Complexity Guide       │");
        System.out.println("  │  0. Exit                        │");
        System.out.println("  └─────────────────────────────────┘" + RESET);
    }

    private static void printComplexityGuide() {
        System.out.println(CYAN + BOLD + "  ── Time Complexity Guide ──" + RESET);
        System.out.println();
        System.out.println("  Operation              | Data Structure         | Complexity");
        System.out.println("  " + "─".repeat(60));
        System.out.println("  addTask()              | PriorityQueue (Heap)   | O(log n)");
        System.out.println("  executeNext()          | PriorityQueue poll     | O(log n)");
        System.out.println("  deleteTask()           | PriorityQueue remove   | O(n)");
        System.out.println("  viewAll()              | Sort copy              | O(n log n)");
        System.out.println("  taskMap lookup (by ID) | HashMap get            | O(1) average");
        System.out.println("  addDependency()        | Cycle check (BFS/DFS)  | O(V + E)");
        System.out.println("  displayOrder()         | Kahn's BFS Topo Sort   | O(V + E)");
        System.out.println("  executeAll()           | Repeated heap poll     | O(n log n)");
        System.out.println();
        System.out.println("  Space Complexity: O(V + E)  — heap + map + adjacency list");
        System.out.println();
        System.out.println("  Where n = total tasks, V = vertices (tasks), E = dependency edges.");
        System.out.println();
        System.out.println(YELLOW + "  Real-world analogues:" + RESET);
        System.out.println("  • OS process scheduling    — CPU assigns time-slices by priority");
        System.out.println("  • Build systems (Make/Gradle) — compile targets in dependency order");
        System.out.println("  • Job queues (Celery/SQS)  — worker picks highest-priority job");
        System.out.println("  • Hospital triage          — treat critical patients first");
    }

    private static void printGoodbye() {
        System.out.println(GREEN + BOLD + "  Goodbye! Tasks saved in memory (no persistence in this demo)." + RESET);
    }

    // ── I/O Utilities ─────────────────────────────────────────────────────────

    /** Read an integer, re-prompting on bad input. */
    private static int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = sc.nextLine().trim();
            try {
                return Integer.parseInt(line);
            } catch (NumberFormatException e) {
                System.out.println(RED + "  Please enter a valid integer." + RESET);
            }
        }
    }

    // ── Demo Seed Data ────────────────────────────────────────────────────────

    /**
     * Pre-loads demo tasks so users can immediately test all features
     * without manually adding tasks from scratch.
     */
    private static void seedDemoTasks() {
        System.out.println(YELLOW + "  Loading demo tasks..." + RESET);
        LocalDateTime tomorrow = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0);
        LocalDateTime next2h   = LocalDateTime.now().plusHours(2);

        scheduler.addTask(new Task("Deploy Production Release", Task.Priority.HIGH, next2h));
        scheduler.addTask(new Task("Fix Critical Security Bug",  Task.Priority.HIGH));
        scheduler.addTask(new Task("Write Unit Tests",           Task.Priority.MEDIUM, tomorrow));
        scheduler.addTask(new Task("Update Documentation",       Task.Priority.LOW));
        scheduler.addTask(new Task("Code Review PR #42",         Task.Priority.MEDIUM));
        scheduler.addTask(new Task("Database Backup",            Task.Priority.HIGH));

        System.out.println(GREEN + "  6 demo tasks loaded. Explore away!\n" + RESET);
    }
}
