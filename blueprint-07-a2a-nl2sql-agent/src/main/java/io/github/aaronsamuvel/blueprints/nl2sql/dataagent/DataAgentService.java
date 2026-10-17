package io.github.aaronsamuvel.blueprints.nl2sql.dataagent;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.github.aaronsamuvel.blueprints.nl2sql.workflow.AgentStep;
import io.github.aaronsamuvel.blueprints.nl2sql.workflow.ContextCompactor;
import io.github.aaronsamuvel.blueprints.nl2sql.workflow.SequentialAgent;
import io.github.aaronsamuvel.blueprints.nl2sql.workflow.WorkflowContext;

/**
 * The DataAgent workflow:
 * {@code SequentialAgent(schema linking -> SelfHealingSqlLoopAgent -> ExecutiveInsightSynthesizer)},
 * with context compaction and a tool-call guard.
 */
public class DataAgentService {

    public record DataAgentResponse(boolean success, DataAnalysisReport report, List<String> linkedTables,
                                    List<SelfHealingSqlLoopAgent.Attempt> attempts, int selfHealingRetries, boolean guardBlocked,
                                    List<String> transcript, int compactions, long elapsedMs) {}

    private final SchemaVectorIndexer indexer;
    private final SelfHealingSqlLoopAgent loop;
    private final ExecutiveInsightSynthesizer synthesizer;
    private final ContextCompactor compactor;
    private final int topK;
    private final int maxToolCalls;

    public DataAgentService(SchemaVectorIndexer indexer, SelfHealingSqlLoopAgent loop, ExecutiveInsightSynthesizer synthesizer,
                            ContextCompactor compactor, int topK, int maxToolCalls) {
        this.indexer = indexer;
        this.loop = loop;
        this.synthesizer = synthesizer;
        this.compactor = compactor;
        this.topK = topK;
        this.maxToolCalls = maxToolCalls;
    }

    public DataAgentResponse ask(String tenantId, String question) {
        if (question == null || question.isBlank()) throw new IllegalArgumentException("question is required");
        long t0 = System.nanoTime();
        WorkflowContext ctx = new WorkflowContext(maxToolCalls).put("tenantId", tenantId).put("question", question);
        AgentStep link = AgentStep.named("schema-linking", c -> {
            List<TableDescriptor> tables = indexer.relevantTables(question, topK);
            c.put("linkedTables", tables.stream().map(TableDescriptor::name).toList());
            c.put("linkedSchema", tables.stream().map(TableDescriptor::render).collect(Collectors.joining("\n")));
        });
        AgentStep synthesize = AgentStep.named("insight", c -> {
            if (c.get("rows").isPresent()) {
                c.put("report", synthesizer.synthesize(question, c.require("sql"), c.<List<Map<String, Object>>>require("rows")));
            }
        });
        new SequentialAgent("data-agent", compactor, link, loop, synthesize).run(ctx);
        List<SelfHealingSqlLoopAgent.Attempt> attempts = ctx.require("attempts");
        DataAnalysisReport report = ctx.<DataAnalysisReport>get("report").orElse(null);
        return new DataAgentResponse(report != null, report, ctx.require("linkedTables"), List.copyOf(attempts),
                Math.max(0, attempts.size() - 1), ctx.<Boolean>get("guardBlocked").orElse(false), ctx.transcript(), ctx.compactions(),
                Duration.ofNanos(System.nanoTime() - t0).toMillis());
    }
}
