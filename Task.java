package scheduler;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a Task in the Task Scheduler system.
 * Implements Comparable to support natural ordering in a PriorityQueue.
 *
 * Time Complexity: O(1) for all attribute access operations.
 */
public class Task implements Comparable<Task> {

    // Enum for human-readable priority labels
    public enum Priority {
        HIGH(1),
        MEDIUM(2),
        LOW(3);

        private final int value;

        Priority(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static Priority fromValue(int value) {
            for (Priority p : Priority.values()) {
                if (p.value == value) return p;
            }
            throw new IllegalArgumentException("Invalid priority value: " + value + ". Use 1 (HIGH), 2 (MEDIUM), or 3 (LOW).");
        }

        public static Priority fromString(String s) {
            return switch (s.trim().toUpperCase()) {
                case "HIGH",   "H", "1" -> HIGH;
                case "MEDIUM", "M", "2" -> MEDIUM;
                case "LOW",    "L", "3" -> LOW;
                default -> throw new IllegalArgumentException("Unknown priority: " + s);
            };
        }
    }

    // ── Fields ────────────────────────────────────────────────────────────────
    private final String id;
    private final String name;
    private final Priority priority;
    private final LocalDateTime scheduledTime;   // nullable
    private final LocalDateTime createdAt;
    private final List<String> dependencyIds;    // IDs of tasks that must run first

    // ── Constructor ───────────────────────────────────────────────────────────
    public Task(String name, Priority priority, LocalDateTime scheduledTime) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Task name cannot be null or empty.");
        }
        this.id            = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.name          = name.trim();
        this.priority      = priority;
        this.scheduledTime = scheduledTime;
        this.createdAt     = LocalDateTime.now();
        this.dependencyIds = new ArrayList<>();
    }

    /** Convenience constructor without scheduled time. */
    public Task(String name, Priority priority) {
        this(name, priority, null);
    }

    // ── Comparable: lower priority-value = higher urgency (MAX-priority queue) ─
    @Override
    public int compareTo(Task other) {
        // Primary: priority value (1=HIGH beats 3=LOW)
        int cmp = Integer.compare(this.priority.getValue(), other.priority.getValue());
        if (cmp != 0) return cmp;
        // Tie-break: earlier scheduled time wins
        if (this.scheduledTime != null && other.scheduledTime != null) {
            return this.scheduledTime.compareTo(other.scheduledTime);
        }
        // Tie-break: earlier creation time wins
        return this.createdAt.compareTo(other.createdAt);
    }

    // ── Dependency helpers ────────────────────────────────────────────────────
    public void addDependency(String taskId) {
        if (!dependencyIds.contains(taskId)) {
            dependencyIds.add(taskId);
        }
    }

    public List<String> getDependencyIds() {
        return new ArrayList<>(dependencyIds);
    }

    public boolean hasDependencies() {
        return !dependencyIds.isEmpty();
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public String getId()                    { return id; }
    public String getName()                  { return name; }
    public Priority getPriority()            { return priority; }
    public LocalDateTime getScheduledTime()  { return scheduledTime; }
    public LocalDateTime getCreatedAt()      { return createdAt; }

    // ── Display helpers ───────────────────────────────────────────────────────
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    public String toString() {
        String timeStr = (scheduledTime != null) ? scheduledTime.format(FMT) : "N/A";
        String depsStr = dependencyIds.isEmpty() ? "None" : String.join(", ", dependencyIds);
        return String.format(
            "| %-8s | %-25s | %-6s | %-16s | %s",
            id, name, priority.name(), timeStr, depsStr
        );
    }

    public String toShortString() {
        return String.format("[%s] %s (%s)", id, name, priority.name());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Task t)) return false;
        return id.equals(t.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
