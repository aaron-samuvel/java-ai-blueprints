package io.github.aaronsamuvel.blueprints.mcp.ops;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordingStream;

import org.springframework.beans.factory.DisposableBean;

/**
 * In-process JFR stream keeping the last {@value #KEEP} {@code jdk.GarbageCollection} and
 * {@code jdk.VirtualThreadPinned} events. Starts on construction; stops with the context.
 */
public class JfrEventMonitor implements DisposableBean {

    public static final int KEEP = 50;

    public record Observed(String event, long durationMicros, String detail) {
        public String summary() {
            return event + " " + (durationMicros / 1000.0) + " ms" + (detail.isEmpty() ? "" : " at " + detail);
        }
    }

    private final Deque<Observed> gc = new ArrayDeque<>();
    private final Deque<Observed> pinned = new ArrayDeque<>();
    private final Duration pinnedThreshold;
    private RecordingStream stream;

    public JfrEventMonitor(Duration pinnedThreshold) {
        this.pinnedThreshold = pinnedThreshold;
        try {
            RecordingStream rs = new RecordingStream();
            rs.enable("jdk.GarbageCollection");
            rs.enable("jdk.VirtualThreadPinned").withThreshold(pinnedThreshold).withStackTrace();
            rs.onEvent("jdk.GarbageCollection", e -> add(gc, new Observed(e.getString("name") + " (" + e.getString("cause") + ")",
                    e.getDuration().toNanos() / 1000, "")));
            rs.onEvent("jdk.VirtualThreadPinned", e -> add(pinned, new Observed("VirtualThreadPinned", e.getDuration().toNanos() / 1000, top(e))));
            rs.setMaxAge(Duration.ofMinutes(5));
            rs.startAsync();
            this.stream = rs;
        } catch (RuntimeException | Error e) {
            this.stream = null; // JFR unavailable on this JVM; tools report it
        }
    }

    private static String top(RecordedEvent e) {
        if (e.getStackTrace() == null) return "";
        return e.getStackTrace().getFrames().stream().filter(RecordedFrame::isJavaFrame)
                .filter(f -> !f.getMethod().getType().getName().startsWith("java."))
                .filter(f -> !f.getMethod().getType().getName().startsWith("jdk."))
                .findFirst()
                .map(f -> f.getMethod().getType().getName() + "." + f.getMethod().getName() + ":" + f.getLineNumber())
                .orElse("");
    }

    private static synchronized void add(Deque<Observed> q, Observed o) {
        synchronized (q) {
            q.addFirst(o);
            while (q.size() > KEEP) q.removeLast();
        }
    }

    public List<Observed> recentGcPauses() {
        synchronized (gc) {
            return List.copyOf(gc);
        }
    }

    public List<Observed> recentPinnedEvents() {
        synchronized (pinned) {
            return List.copyOf(pinned);
        }
    }

    public boolean running() {
        return stream != null;
    }

    public Duration pinnedThreshold() {
        return pinnedThreshold;
    }

    @Override
    public void destroy() {
        if (stream != null) stream.close();
    }
}
