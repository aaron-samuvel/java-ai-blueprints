package io.github.aaronsamuvel.blueprints.mcp.host;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.ai.chat.model.ChatModel;

/**
 * Intelligence switchboard (handbook Part III, Blueprint 6, "ModelSwitchboardRouter"). For each
 * task it looks at:
 * <ul>
 *   <li><b>privacy:</b> personal data, or a caller flag, forces the local sovereign model;</li>
 *   <li><b>complexity:</b> a 0..1 heuristic score; at or above {@code frontierThreshold} the task goes to the frontier model;</li>
 *   <li><b>latency budget:</b> a budget below {@code fastBudget} rules out the frontier model.</li>
 * </ul>
 * Cloud calls run on virtual threads with a timeout of {@code min(budget, cloudTimeout)}. On timeout
 * or error the task falls back to the local model, and the answer says so. The weights and
 * thresholds are the author's design; the handbook names the inputs, not the formula.
 */
public class ModelSwitchboardRouter implements AutoCloseable {

    public record Decision(ModelTarget target, double complexity, boolean privacySensitive, String reason) {}

    public record RoutedAnswer(Decision decision, ModelTarget used, boolean fellBack, String fallbackReason, long latencyMs, String answer) {}

    private static final Pattern PII = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}|\\b[A-Z]{5}[0-9]{4}[A-Z]\\b|\\b\\d{3}-\\d{2}-\\d{4}\\b|(?:\\+91[ -]?)?\\b[6-9]\\d{9}\\b|\\b(?:\\d[ -]?){13,19}\\b");
    private static final List<String> SYNTHESIS = List.of("design", "architecture", "trade-off", "tradeoff", "compare", "strategy",
            "explain why", "root cause", "plan", "synthesi", "evaluate", "migrate", "step by step");
    private static final List<String> SIMPLE = List.of("classify", "intent", "route", "yes or no", "label", "which category");

    private final Map<ModelTarget, ChatModel> models = new EnumMap<>(ModelTarget.class);
    private final Duration cloudTimeout;
    private final Duration fastBudget;
    private final double frontierThreshold;
    private final ExecutorService calls = Executors.newVirtualThreadPerTaskExecutor();

    public ModelSwitchboardRouter(Map<ModelTarget, ChatModel> models, Duration cloudTimeout, Duration fastBudget, double frontierThreshold) {
        if (!models.containsKey(ModelTarget.LOCAL_SOVEREIGN)) throw new IllegalArgumentException("a LOCAL_SOVEREIGN model is required (it is the fallback)");
        this.models.putAll(models);
        this.cloudTimeout = cloudTimeout;
        this.fastBudget = fastBudget;
        this.frontierThreshold = frontierThreshold;
    }

    static double complexity(String prompt) {
        String p = prompt.toLowerCase(Locale.ROOT);
        double score = Math.min(prompt.length() / 1200.0, 0.4);
        score += Math.min(SYNTHESIS.stream().filter(p::contains).count() * 0.2, 0.5);
        score += Math.min((p.chars().filter(c -> c == '?').count() - 1) * 0.05, 0.1);
        if (SIMPLE.stream().anyMatch(p::contains)) score -= 0.3;
        return Math.max(0, Math.min(1, Math.round(score * 100) / 100.0));
    }

    public Decision decide(TaskProfile task) {
        boolean pii = task.privacySensitive() || PII.matcher(task.prompt()).find();
        double c = complexity(task.prompt());
        if (pii) return new Decision(ModelTarget.LOCAL_SOVEREIGN, c, true, "privacy-sensitive: stays on the local sovereign model");
        String p = task.prompt().toLowerCase(Locale.ROOT);
        if (SIMPLE.stream().anyMatch(p::contains) && c < 0.3) return new Decision(ModelTarget.LOCAL_SOVEREIGN, c, false, "simple classification/routing task");
        if (c >= frontierThreshold && task.latencyBudget().compareTo(fastBudget) >= 0 && models.containsKey(ModelTarget.CLOUD_FRONTIER)) {
            return new Decision(ModelTarget.CLOUD_FRONTIER, c, false, "complex synthesis (complexity " + c + ")");
        }
        if (models.containsKey(ModelTarget.CLOUD_FAST)) {
            return new Decision(ModelTarget.CLOUD_FAST, c, false, c >= frontierThreshold
                    ? "complex, but the latency budget " + task.latencyBudget().toMillis() + " ms is below " + fastBudget.toMillis() + " ms"
                    : "moderate task (complexity " + c + ")");
        }
        return new Decision(ModelTarget.LOCAL_SOVEREIGN, c, false, "no cloud model configured");
    }

    public RoutedAnswer ask(TaskProfile task) {
        Decision d = decide(task);
        long t0 = System.nanoTime();
        if (d.target() == ModelTarget.LOCAL_SOVEREIGN) {
            return new RoutedAnswer(d, d.target(), false, null, ms(t0), call(ModelTarget.LOCAL_SOVEREIGN, task.prompt()));
        }
        Duration timeout = task.latencyBudget().compareTo(cloudTimeout) < 0 ? task.latencyBudget() : cloudTimeout;
        CompletableFuture<String> f = CompletableFuture.supplyAsync(() -> call(d.target(), task.prompt()), calls);
        String reason;
        try {
            return new RoutedAnswer(d, d.target(), false, null, ms(t0), f.get(timeout.toMillis(), TimeUnit.MILLISECONDS));
        } catch (TimeoutException e) {
            f.cancel(true);
            reason = d.target() + " did not answer within " + timeout.toMillis() + " ms";
        } catch (ExecutionException e) {
            reason = d.target() + " failed: " + e.getCause().getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            reason = "interrupted";
        }
        return new RoutedAnswer(d, ModelTarget.LOCAL_SOVEREIGN, true, reason, ms(t0), call(ModelTarget.LOCAL_SOVEREIGN, task.prompt()));
    }

    /**
     * Structured extraction on the routed model, validated against {@code type}'s JSON schema by
     * Spring AI's {@link StructuredOutputValidationAdvisor}, which retries with the validation error.
     */
    public <T> T extract(TaskProfile task, Class<T> type, int maxAttempts) {
        Decision d = decide(task);
        return ChatClient.builder(models.get(d.target()))
                .defaultAdvisors(StructuredOutputValidationAdvisor.builder().outputType(type).maxRepeatAttempts(maxAttempts).build())
                .build().prompt().user(task.prompt()).call().entity(type);
    }

    public List<ModelTarget> configured() {
        return new ArrayList<>(models.keySet());
    }

    private String call(ModelTarget target, String prompt) {
        return ChatClient.create(models.get(target)).prompt().user(prompt).call().content();
    }

    private static long ms(long t0) {
        return (System.nanoTime() - t0) / 1_000_000;
    }

    @Override
    public void close() {
        calls.shutdownNow();
    }
}
