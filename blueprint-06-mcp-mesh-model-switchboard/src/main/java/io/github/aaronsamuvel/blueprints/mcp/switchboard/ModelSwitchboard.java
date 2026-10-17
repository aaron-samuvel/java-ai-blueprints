package io.github.aaronsamuvel.blueprints.mcp.switchboard;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

/**
 * Routes each request to a model tier by task type, then applies the caller's entitlements:
 * <pre>
 * CHAT     -> LOCAL_FAST
 * ANALYSIS -> LOCAL_SMART
 * CRITICAL -> PREMIUM if the token has scope models.premium, otherwise downgraded to LOCAL_SMART
 * </pre>
 * Missing tiers fall back to the next cheaper configured tier.
 */
public class ModelSwitchboard {

    public record Routed(ModelTier requested, ModelTier used, boolean downgraded, String answer) {}

    private final Map<ModelTier, ChatClient> clients = new EnumMap<>(ModelTier.class);

    public ModelSwitchboard(Map<ModelTier, ChatModel> models) {
        if (models.isEmpty()) throw new IllegalArgumentException("at least one model required");
        models.forEach((tier, model) -> clients.put(tier, ChatClient.create(model)));
    }

    public static ModelTier wanted(TaskType task) {
        return switch (task) {
            case CHAT -> ModelTier.LOCAL_FAST;
            case ANALYSIS -> ModelTier.LOCAL_SMART;
            case CRITICAL -> ModelTier.PREMIUM;
        };
    }

    public ModelTier route(TaskType task, Set<String> scopes) {
        ModelTier wanted = wanted(task);
        if (wanted == ModelTier.PREMIUM && !scopes.contains("models.premium")) wanted = ModelTier.LOCAL_SMART;
        return available(wanted);
    }

    public Routed ask(TaskType task, Set<String> scopes, String message) {
        ModelTier requested = wanted(task);
        ModelTier used = route(task, scopes);
        String answer = clients.get(used).prompt().user(message).call().content();
        return new Routed(requested, used, used != requested, answer);
    }

    private ModelTier available(ModelTier wanted) {
        for (int i = wanted.ordinal(); i >= 0; i--) {
            ModelTier t = ModelTier.values()[i];
            if (clients.containsKey(t)) return t;
        }
        return clients.keySet().iterator().next();
    }
}
