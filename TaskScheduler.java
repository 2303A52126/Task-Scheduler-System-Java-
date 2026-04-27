package scheduler;

import java.util.*;

/**
 * TaskScheduler — Core scheduling engine.
 *
 * Data Structures used:
 *   • PriorityQueue<Task>     — O(log n) insert / O(log n) remove-min
 *   • HashMap<String, Task>   — O(1) average lookup by ID
 *   • HashMap + adjacency list — O(V+E) dependency graph (Kahn's BFS topological sort)
 *
 * Time Complexity Summary:
 *   addTask()         → O(log n)   — heap insertion
 *   executeNext()     → O(log n)   — heap poll
 *   deleteTask()      → O(n)       — linear scan + re-heap (Java PQ limitation)
 *   viewAll()         → O(n log n) — sort copy for display
 *   topologicalOrder()→ O(V + E)   — Kahn's BFS
 */
public class TaskScheduler {

    // ── Internal State ────────────────────────────────────────────────────────

    /** Min-heap on Task.compareTo (HIGH priority at top). */
    private final PriorityQueue<Task> heap;

    /** Fast O(1) lookup and existence checks. */
    private final Map<String, Task> taskMap;

    /** Tracks IDs of tasks already executed (for dependency resolution). */
    private final Set<String> executedIds;

    /** Execution history log. */
    private final List<String> executionLog;

    // ── Constructor ───────────────────────────────────────────────────────────
    public TaskScheduler() {
        this.heap         = new PriorityQueue<>();
        this.taskMap      = new LinkedHashMap<>();
        this.executedIds  = new HashSet<>();
        this.executionLog = new ArrayList<>();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Add a new task to the scheduler.
     * Rejects duplicates by name (case-insensitive).
     * Time: O(log n)
     */
    public boolean addTask(Task task) {
        // Check for duplicate names
        boolean nameExists = taskMap.values().stream()
                .anyMatch(t -> t.getName().equalsIgnoreCase(task.getName()));
        if (nameExists) {
            System.out.printf("  [WARN] A task named \"%s\" already exists. Skipped.%n", task.getName());
            return false;
        }
        heap.offer(task);
        taskMap.put(task.getId(), task);
        System.out.printf("  [OK] Task added: %s%n", task.toShortString());
        return true;
    }

    /**
     * Execute the highest-priority eligible task.
     * A task is eligible if all its dependencies have been executed.
     * Time: O(n log n) worst-case (if many tasks are dependency-blocked)
     */
    public Optional<Task> executeNext() {
        if (heap.isEmpty()) {
            System.out.println("  [INFO] No tasks available to execute.");
            return Optional.empty();
        }

        // We may need to skip dependency-blocked tasks
        List<Task> skipped = new ArrayList<>();
        Task chosen = null;

        while (!heap.isEmpty()) {
            Task candidate = heap.poll();
            if (isDependencySatisfied(candidate)) {
                chosen = candidate;
                break;
            } else {
                System.out.printf("  [SKIP] \"%s\" is blocked (unsatisfied dependencies: %s)%n",
                        candidate.getName(), getUnsatisfiedDeps(candidate));
                skipped.add(candidate);
            }
        }

        // Re-insert skipped tasks back into heap
        for (Task t : skipped) {
            heap.offer(t);
        }

        if (chosen == null) {
            System.out.println("  [WARN] All remaining tasks are blocked by unresolved dependencies.");
            return Optional.empty();
        }

        // Mark as executed
        taskMap.remove(chosen.getId());
        executedIds.add(chosen.getId());
        String logEntry = String.format("[EXECUTED] %s | Priority: %s | Time: %s",
                chosen.getName(), chosen.getPriority().name(),
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
        executionLog.add(logEntry);

        return Optional.of(chosen);
    }

    /**
     * Execute ALL tasks in priority order (respecting dependencies).
     */
    public void executeAll() {
        if (isEmpty()) {
            System.out.println("  [INFO] No tasks to execute.");
            return;
        }
        System.out.println("\n  Executing all tasks in priority order...");
        System.out.println("  " + "─".repeat(55));
        int count = 1;
        while (!isEmpty()) {
            Optional<Task> result = executeNext();
            if (result.isPresent()) {
                System.out.printf("  %2d. ✔  %s%n", count++, result.get().toShortString());
            } else {
                break; // Remaining tasks are all blocked — prevent infinite loop
            }
        }
    }

    /**
     * Delete a task by ID without executing it.
     * Time: O(n) — Java's PriorityQueue remove(Object) is O(n)
     */
    public boolean deleteTask(String taskId) {
        Task task = taskMap.get(taskId.toUpperCase());
        if (task == null) {
            System.out.printf("  [WARN] Task ID \"%s\" not found.%n", taskId);
            return false;
        }
        heap.remove(task);
        taskMap.remove(task.getId());
        System.out.printf("  [OK] Task deleted: %s%n", task.toShortString());
        return true;
    }

    /**
     * Add a dependency edge: taskId depends on dependsOnId.
     * Uses cycle detection to prevent deadlocks.
     */
    public boolean addDependency(String taskId, String dependsOnId) {
        Task task      = taskMap.get(taskId.toUpperCase());
        Task dependsOn = taskMap.get(dependsOnId.toUpperCase());

        if (task == null) {
            System.out.printf("  [ERROR] Task ID \"%s\" not found.%n", taskId);
            return false;
        }
        if (dependsOn == null) {
            System.out.printf("  [ERROR] Task ID \"%s\" not found.%n", dependsOnId);
            return false;
        }
        if (taskId.equalsIgnoreCase(dependsOnId)) {
            System.out.println("  [ERROR] A task cannot depend on itself.");
            return false;
        }
        // Cycle check: if dependsOn already (directly/transitively) depends on task → cycle
        if (wouldCreateCycle(taskId.toUpperCase(), dependsOnId.toUpperCase())) {
            System.out.printf("  [ERROR] Adding this dependency would create a cycle.%n");
            return false;
        }
        task.addDependency(dependsOnId.toUpperCase());
        System.out.printf("  [OK] \"%s\" now depends on \"%s\"%n", task.getName(), dependsOn.getName());
        return true;
    }

    /**
     * Display all pending tasks sorted by priority.
     * Time: O(n log n) for sorting
     */
    public void viewAll() {
        if (taskMap.isEmpty()) {
            System.out.println("  [INFO] No pending tasks.");
            return;
        }
        List<Task> sorted = new ArrayList<>(taskMap.values());
        Collections.sort(sorted);

        System.out.println();
        printTableHeader();
        for (Task t : sorted) {
            System.out.println("  " + t);
        }
        printTableFooter();
        System.out.printf("  Total pending tasks: %d%n", sorted.size());
    }

    /**
     * Display the recommended execution order using topological sort (Kahn's BFS).
     * Falls back to pure priority order if no dependencies exist.
     * Time: O(V + E)
     */
    public void displayExecutionOrder() {
        if (taskMap.isEmpty()) {
            System.out.println("  [INFO] No tasks to display.");
            return;
        }

        List<Task> order = topologicalSort();
        System.out.println("\n  Recommended Execution Order:");
        System.out.println("  " + "─".repeat(45));
        for (int i = 0; i < order.size(); i++) {
            System.out.printf("  %2d.  %s%n", i + 1, order.get(i).toShortString());
        }

        if (!executedIds.isEmpty()) {
            System.out.printf("%n  Already executed: %d task(s)%n", executedIds.size());
        }
    }

    /**
     * Print execution history log.
     */
    public void viewExecutionLog() {
        if (executionLog.isEmpty()) {
            System.out.println("  [INFO] No tasks have been executed yet.");
            return;
        }
        System.out.println("\n  Execution History:");
        System.out.println("  " + "─".repeat(55));
        for (int i = 0; i < executionLog.size(); i++) {
            System.out.printf("  %2d. %s%n", i + 1, executionLog.get(i));
        }
    }

    /** @return true if there are no pending tasks. */
    public boolean isEmpty() {
        return heap.isEmpty();
    }

    /** @return count of pending tasks. */
    public int size() {
        return heap.size();
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    /**
     * Returns true if all dependencies of the task have been executed.
     */
    private boolean isDependencySatisfied(Task task) {
        for (String depId : task.getDependencyIds()) {
            if (!executedIds.contains(depId)) return false;
        }
        return true;
    }

    private List<String> getUnsatisfiedDeps(Task task) {
        List<String> unsat = new ArrayList<>();
        for (String depId : task.getDependencyIds()) {
            if (!executedIds.contains(depId)) {
                Task dep = taskMap.get(depId);
                unsat.add(dep != null ? dep.getName() : depId);
            }
        }
        return unsat;
    }

    /**
     * Cycle detection via DFS.
     * Returns true if making taskId depend on dependsOnId would create a cycle.
     */
    private boolean wouldCreateCycle(String taskId, String dependsOnId) {
        // BFS/DFS: can we reach taskId starting from dependsOnId?
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(dependsOnId);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (current.equals(taskId)) return true;
            if (visited.add(current)) {
                Task t = taskMap.get(current);
                if (t != null) {
                    queue.addAll(t.getDependencyIds());
                }
            }
        }
        return false;
    }

    /**
     * Kahn's BFS Topological Sort with priority tie-breaking.
     * Nodes with in-degree 0 are placed in a PriorityQueue so highest-priority
     * tasks are recommended first within each "layer".
     *
     * Time: O((V + E) log V)
     */
    private List<Task> topologicalSort() {
        // Build in-degree map
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> dependents = new HashMap<>(); // depId -> list of tasks that depend on it

        for (Task t : taskMap.values()) {
            inDegree.putIfAbsent(t.getId(), 0);
            for (String depId : t.getDependencyIds()) {
                inDegree.merge(t.getId(), 1, Integer::sum);
                dependents.computeIfAbsent(depId, k -> new ArrayList<>()).add(t.getId());
            }
        }

        // Seed the priority queue with tasks that have no dependencies
        PriorityQueue<Task> ready = new PriorityQueue<>();
        for (Task t : taskMap.values()) {
            if (inDegree.getOrDefault(t.getId(), 0) == 0) {
                ready.offer(t);
            }
        }

        List<Task> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            Task next = ready.poll();
            order.add(next);
            // Reduce in-degree for tasks that depended on this one
            for (String depTaskId : dependents.getOrDefault(next.getId(), Collections.emptyList())) {
                int newDegree = inDegree.merge(depTaskId, -1, Integer::sum);
                if (newDegree == 0) {
                    Task t = taskMap.get(depTaskId);
                    if (t != null) ready.offer(t);
                }
            }
        }

        // If order doesn't contain all tasks, there's a cycle (shouldn't happen with our guard)
        if (order.size() < taskMap.size()) {
            System.out.println("  [WARN] Cycle detected — showing partial order.");
        }
        return order;
    }

    // ── Display Helpers ───────────────────────────────────────────────────────
    private void printTableHeader() {
        System.out.println("  " + "─".repeat(85));
        System.out.printf("  | %-8s | %-25s | %-6s | %-16s | %s%n",
                "ID", "NAME", "PRI", "SCHEDULED", "DEPENDENCIES");
        System.out.println("  " + "─".repeat(85));
    }

    private void printTableFooter() {
        System.out.println("  " + "─".repeat(85));
    }
}
