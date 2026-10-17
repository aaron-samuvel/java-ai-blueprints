package io.github.aaronsamuvel.blueprints.goap;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import io.github.aaronsamuvel.blueprints.goap.ooda.BillingCsv;
import io.github.aaronsamuvel.blueprints.goap.ooda.CostDiagnosis.Cause;
import io.github.aaronsamuvel.blueprints.goap.ooda.CostSample;
import io.github.aaronsamuvel.blueprints.goap.ooda.FinOpsIncidentAgent;
import io.github.aaronsamuvel.blueprints.goap.ooda.IncidentReport;
import io.github.aaronsamuvel.blueprints.goap.ooda.IncidentReport.Outcome;
import io.github.aaronsamuvel.blueprints.shared.StubChatModel;

import org.junit.jupiter.api.Test;

class FinOpsIncidentAgentTest {

    private static final List<CostSample> SPIKE = BillingCsv.classpath("billing-sample.csv"); // batch-etl 800 -> 6400

    private static FinOpsIncidentAgent agent(StubChatModel model, String autoApproveLimit) {
        return new FinOpsIncidentAgent(model, new BigDecimal("1.5"), new BigDecimal(autoApproveLimit), 10);
    }

    private static StubChatModel diagnosis(String cause) {
        return StubChatModel.fixed("{\"cause\":\"" + cause + "\",\"explanation\":\"test\"}");
    }

    @Test
    void idleResourcesAreScaledDownAndResolved() {
        StubChatModel model = diagnosis("IDLE_RESOURCES");
        IncidentReport r = agent(model, "10000").run(SPIKE);
        assertThat(r.outcome()).isEqualTo(Outcome.RESOLVED);
        assertThat(r.service()).isEqualTo("batch-etl");
        assertThat(r.cause()).isEqualTo(Cause.IDLE_RESOURCES);
        assertThat(r.todayInr()).isEqualByComparingTo("6400.00");
        assertThat(r.baselineInr()).isEqualByComparingTo("803.33");
        // OODA re-planning: before diagnosis the only known route is escalation; afterwards a cheaper route appears
        assertThat(r.trace()).anyMatch(t -> t.startsWith("cycle 2: plan=diagnose>escalateToHuman"));
        assertThat(r.trace()).anyMatch(t -> t.startsWith("cycle 3: plan=scaleDownIdle>notifyOwner"));
        assertThat(model.received()).hasSize(1);
        assertThat(model.allSentText()).contains("batch-etl").contains("6400.00");
    }

    @Test
    void smallRunawayJobIsAutoApprovedAndStopped() {
        IncidentReport r = agent(diagnosis("RUNAWAY_JOB"), "10000").run(SPIKE); // excess ~5597 <= 10000
        assertThat(r.outcome()).isEqualTo(Outcome.RESOLVED);
        assertThat(r.trace()).anyMatch(t -> t.contains("approval auto-granted"))
                .anyMatch(t -> t.contains("stopped runaway job on batch-etl"));
    }

    @Test
    void largeRunawayJobIsDeniedAndReplannedToEscalation() {
        IncidentReport r = agent(diagnosis("RUNAWAY_JOB"), "1000").run(SPIKE);
        assertThat(r.outcome()).isEqualTo(Outcome.ESCALATED);
        assertThat(r.trace()).anyMatch(t -> t.contains("approval denied"))
                .anyMatch(t -> t.contains("plan=escalateToHuman act=escalateToHuman"))
                .noneMatch(t -> t.contains("stopped runaway job"));
    }

    @Test
    void unparseableDiagnosisDegradesToUnknownAndEscalates() {
        IncidentReport r = agent(StubChatModel.fixed("I think it is probably fine?"), "10000").run(SPIKE);
        assertThat(r.cause()).isEqualTo(Cause.UNKNOWN);
        assertThat(r.outcome()).isEqualTo(Outcome.ESCALATED);
    }

    @Test
    void steadySpendClosesWithoutCallingTheModel() {
        StubChatModel model = diagnosis("IDLE_RESOURCES");
        List<CostSample> steady = SPIKE.stream().filter(s -> s.service().equals("search-api")).toList();
        IncidentReport r = agent(model, "10000").run(steady);
        assertThat(r.outcome()).isEqualTo(Outcome.NO_INCIDENT);
        assertThat(model.received()).isEmpty();
        assertThat(r.trace()).hasSize(2);
    }
}
