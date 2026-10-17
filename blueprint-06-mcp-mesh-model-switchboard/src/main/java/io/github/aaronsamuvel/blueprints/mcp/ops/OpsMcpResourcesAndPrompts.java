package io.github.aaronsamuvel.blueprints.mcp.ops;

import java.util.List;

import io.modelcontextprotocol.spec.McpSchema;

import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.McpResource;

/**
 * MCP <b>resources</b> and <b>prompts</b> served next to the ops tools, so a client can read state
 * without calling a tool and can fetch a ready-made investigation prompt. Spring AI 2.0.1 publishes
 * them through its annotation scanner ({@code @McpResource}, {@code @McpPrompt}).
 * The resource URIs and the prompt wording are <b>author's design (not from handbook)</b>.
 */
public class OpsMcpResourcesAndPrompts {

    private final BatchIntrospectionMcpTools batch;
    private final JvmDiagnosticsMcpTools jvm;
    private final List<String> restartableJobs;

    public OpsMcpResourcesAndPrompts(BatchIntrospectionMcpTools batch, JvmDiagnosticsMcpTools jvm, List<String> restartableJobs) {
        this.batch = batch;
        this.jvm = jvm;
        this.restartableJobs = List.copyOf(restartableJobs);
    }

    @McpResource(uri = "ops://batch/jobs", name = "batch-jobs", mimeType = "text/plain",
            description = "Restartable Spring Batch jobs on this node and their recent executions")
    public String batchJobs() {
        StringBuilder sb = new StringBuilder("restart allow-list: " + restartableJobs + "\n");
        for (String job : restartableJobs) sb.append("\n# ").append(job).append('\n').append(batch.listJobExecutions(job, 5)).append('\n');
        return sb.toString();
    }

    @McpResource(uri = "ops://batch/executions/{executionId}", name = "batch-execution", mimeType = "text/plain",
            description = "Failed-step details of one job execution")
    public String batchExecution(String executionId) {
        return batch.failedStepDetails(Long.parseLong(executionId));
    }

    @McpResource(uri = "ops://jvm/heap", name = "jvm-heap", mimeType = "text/plain",
            description = "Current heap usage and memory pools")
    public String jvmHeap() {
        return jvm.heapMemoryPools();
    }

    @McpPrompt(name = "diagnose-failed-batch-job",
            description = "Investigate a failed batch execution and decide whether a guarded restart is safe")
    public McpSchema.GetPromptResult diagnoseFailedBatchJob(
            @McpArg(name = "executionId", description = "the FAILED job execution id", required = true) String executionId) {
        String details = batch.failedStepDetails(Long.parseLong(executionId));
        String text = """
                You are the on-call engineer for the batch platform. Execution %s failed.
                Failed-step details:
                %s

                1. Explain the most likely root cause in two sentences.
                2. Say whether the cause is transient (upstream outage, timeout) or a data/code defect.
                3. Only if it is transient, call restartFailedJob with dryRun=true and a reason of at least 15
                   characters; never set dryRun=false without a human's approval.
                """.formatted(executionId, details);
        return new McpSchema.GetPromptResult("Diagnose execution " + executionId,
                List.of(new McpSchema.PromptMessage(McpSchema.Role.USER, new McpSchema.TextContent(text))));
    }

    @McpPrompt(name = "jvm-health-review", description = "Review heap, GC and virtual-thread pinning on this node")
    public McpSchema.GetPromptResult jvmHealthReview() {
        String text = """
                Review the JVM health of this node. Call heapMemoryPools, gcPauses and virtualThreadPinnedEvents,
                then report: heap headroom, whether GC pauses exceed 200 ms, and any synchronized block that pins
                virtual threads (name the class from the stack top) with a ReentrantLock fix.
                """;
        return new McpSchema.GetPromptResult("JVM health review",
                List.of(new McpSchema.PromptMessage(McpSchema.Role.USER, new McpSchema.TextContent(text))));
    }
}
