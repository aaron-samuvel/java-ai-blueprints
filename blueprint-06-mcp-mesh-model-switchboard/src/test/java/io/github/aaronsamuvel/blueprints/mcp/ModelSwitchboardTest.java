package io.github.aaronsamuvel.blueprints.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;

import io.github.aaronsamuvel.blueprints.mcp.switchboard.ModelSwitchboard;
import io.github.aaronsamuvel.blueprints.mcp.switchboard.ModelTier;
import io.github.aaronsamuvel.blueprints.mcp.switchboard.TaskType;
import io.github.aaronsamuvel.blueprints.shared.StubChatModel;

import org.junit.jupiter.api.Test;

class ModelSwitchboardTest {

    private final StubChatModel fast = StubChatModel.fixed("fast");
    private final StubChatModel smart = StubChatModel.fixed("smart");
    private final StubChatModel premium = StubChatModel.fixed("premium");
    private final ModelSwitchboard sb = new ModelSwitchboard(Map.of(
            ModelTier.LOCAL_FAST, fast, ModelTier.LOCAL_SMART, smart, ModelTier.PREMIUM, premium));

    @Test
    void routesByTask() {
        assertThat(sb.route(TaskType.CHAT, Set.of())).isEqualTo(ModelTier.LOCAL_FAST);
        assertThat(sb.route(TaskType.ANALYSIS, Set.of())).isEqualTo(ModelTier.LOCAL_SMART);
        assertThat(sb.route(TaskType.CRITICAL, Set.of("models.premium"))).isEqualTo(ModelTier.PREMIUM);
    }

    @Test
    void premiumWithoutEntitlementIsDowngradedAndPremiumModelNeverCalled() {
        ModelSwitchboard.Routed r = sb.ask(TaskType.CRITICAL, Set.of("models.use"), "q");
        assertThat(r.used()).isEqualTo(ModelTier.LOCAL_SMART);
        assertThat(r.downgraded()).isTrue();
        assertThat(r.answer()).isEqualTo("smart");
        assertThat(premium.received()).isEmpty();
    }

    @Test
    void missingTierFallsBackToCheaperConfiguredTier() {
        ModelSwitchboard onlyFast = new ModelSwitchboard(Map.of(ModelTier.LOCAL_FAST, fast));
        assertThat(onlyFast.route(TaskType.CRITICAL, Set.of("models.premium"))).isEqualTo(ModelTier.LOCAL_FAST);
    }
}
