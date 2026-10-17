package io.github.aaronsamuvel.blueprints.mcp.ops;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.boot.health.actuate.endpoint.CompositeHealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;

/**
 * Live JVM diagnostics as MCP tools (handbook Part III, Blueprint 6, "JvmDiagnosticsMcpTools"):
 * heap pools and GC from the platform MXBeans, recent GC pauses and virtual-thread pinning from a
 * JFR stream ({@link JfrEventMonitor}), and the actuator health status.
 */
public class JvmDiagnosticsMcpTools {

    private final JfrEventMonitor jfr;
    private final HealthEndpoint health;

    public JvmDiagnosticsMcpTools(JfrEventMonitor jfr, HealthEndpoint health) {
        this.jfr = jfr;
        this.health = health;
    }

    @Tool(description = "Heap usage and each heap memory pool (used, committed, max in MiB)")
    public String heapMemoryPools() {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        List<String> lines = new ArrayList<>();
        lines.add("heap: " + mib(heap.getUsed()) + " used / " + mib(heap.getCommitted()) + " committed / " + mib(heap.getMax()) + " max MiB");
        for (MemoryPoolMXBean p : ManagementFactory.getMemoryPoolMXBeans()) {
            if (p.getType() != MemoryType.HEAP) continue;
            MemoryUsage u = p.getUsage();
            lines.add("pool " + p.getName() + ": " + mib(u.getUsed()) + " used / " + mib(u.getCommitted()) + " committed MiB");
        }
        return String.join("\n", lines);
    }

    @Tool(description = "Garbage collector totals and the most recent GC pauses recorded by JFR")
    public String gcPauses() {
        String totals = ManagementFactory.getGarbageCollectorMXBeans().stream()
                .map((GarbageCollectorMXBean g) -> g.getName() + ": " + g.getCollectionCount() + " collections, " + g.getCollectionTime() + " ms total")
                .collect(Collectors.joining("\n"));
        List<JfrEventMonitor.Observed> recent = jfr.recentGcPauses();
        return totals + "\nrecent pauses (JFR" + (jfr.running() ? "" : ", not running") + "): "
                + (recent.isEmpty() ? "none recorded yet" : recent.stream().map(JfrEventMonitor.Observed::summary).collect(Collectors.joining("; ")));
    }

    @Tool(description = "Recent virtual-thread pinned events (jdk.VirtualThreadPinned) with duration and stack top")
    public String virtualThreadPinnedEvents() {
        List<JfrEventMonitor.Observed> pinned = jfr.recentPinnedEvents();
        if (!jfr.running()) return "JFR stream is not running";
        return pinned.isEmpty() ? "no pinned virtual threads recorded (threshold " + jfr.pinnedThreshold() + ")"
                : pinned.stream().map(JfrEventMonitor.Observed::summary).collect(Collectors.joining("\n"));
    }

    @Tool(description = "Spring Boot actuator health: overall status and each component's status")
    public String actuatorHealth() {
        HealthDescriptor d = health.health();
        StringBuilder sb = new StringBuilder("status " + d.getStatus());
        if (d instanceof CompositeHealthDescriptor c && c.getComponents() != null) {
            c.getComponents().forEach((name, comp) -> sb.append("\n").append(name).append(": ").append(comp.getStatus()));
        }
        return sb.toString();
    }

    private static long mib(long bytes) {
        return bytes < 0 ? -1 : bytes / (1024 * 1024);
    }
}
