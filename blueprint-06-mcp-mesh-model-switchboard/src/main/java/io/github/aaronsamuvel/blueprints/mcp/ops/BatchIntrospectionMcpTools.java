package io.github.aaronsamuvel.blueprints.mcp.ops;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;

/**
 * Spring Batch introspection as MCP tools (handbook Part III, Blueprint 6, "BatchIntrospectionMcpTools").
 * Restarting is guarded:
 * <ul>
 *   <li>the job must be on the restart allow-list;</li>
 *   <li>the execution must be FAILED and must be the latest execution of its instance;</li>
 *   <li>no other execution of the job may be running;</li>
 *   <li>a reason of at least 15 characters is required;</li>
 *   <li>an instance gets at most {@code maxRestarts} restarts;</li>
 *   <li>the default is a dry run that only reports what would happen.</li>
 * </ul>
 */
public class BatchIntrospectionMcpTools {

    private final JobRepository repository;
    private final JobOperator operator;
    private final Set<String> restartable;
    private final int maxRestarts;
    private final int maxTraceLines;

    public BatchIntrospectionMcpTools(JobRepository repository, JobOperator operator, Set<String> restartable,
                                      int maxRestarts, int maxTraceLines) {
        this.repository = repository;
        this.operator = operator;
        this.restartable = Set.copyOf(restartable);
        this.maxRestarts = maxRestarts;
        this.maxTraceLines = maxTraceLines;
    }

    @Tool(description = "List recent executions of a Spring Batch job with status and per-step status")
    public String listJobExecutions(@ToolParam(description = "job name, e.g. ledgerReconciliationJob") String jobName,
                                    @ToolParam(description = "maximum number of job instances to include", required = false) Integer limit) {
        int n = limit == null || limit < 1 ? 10 : Math.min(limit, 50);
        List<JobExecution> executions = new ArrayList<>();
        for (JobInstance i : repository.getJobInstances(jobName, 0, n)) executions.addAll(repository.getJobExecutions(i));
        if (executions.isEmpty()) return "no executions for job " + jobName + "; known jobs: " + repository.getJobNames();
        executions.sort(Comparator.comparingLong(JobExecution::getId).reversed());
        return executions.stream().map(e -> "execution " + e.getId() + " (instance " + e.getJobInstance().getInstanceId() + "): "
                + e.getStatus() + ", exit " + e.getExitStatus().getExitCode() + ", steps "
                + e.getStepExecutions().stream().map(s -> s.getStepName() + "=" + s.getStatus()).toList())
                .collect(Collectors.joining("\n"));
    }

    @Tool(description = "Show the failed steps of a job execution with the exception and a trimmed stack trace")
    public String failedStepDetails(@ToolParam(description = "job execution id") long executionId) {
        JobExecution e = repository.getJobExecution(executionId);
        if (e == null) return "no job execution " + executionId;
        List<String> out = new ArrayList<>();
        for (StepExecution s : e.getStepExecutions()) {
            if (s.getStatus() != BatchStatus.FAILED) continue;
            String trace = s.getExitStatus().getExitDescription();
            String trimmed = Arrays.stream(trace.split("\\R")).limit(maxTraceLines).collect(Collectors.joining("\n  "));
            out.add("step " + s.getStepName() + " FAILED (read " + s.getReadCount() + ", write " + s.getWriteCount() + ")\n  " + trimmed);
        }
        return out.isEmpty() ? "execution " + executionId + " has no failed steps (status " + e.getStatus() + ")" : String.join("\n", out);
    }

    @Tool(description = "Restart a FAILED job execution. Defaults to a dry run; set dryRun=false to actually restart. Requires a reason.")
    public String restartFailedJob(@ToolParam(description = "job execution id") long executionId,
                                   @ToolParam(description = "why it is safe to restart now (at least 15 characters)") String reason,
                                   @ToolParam(description = "true (default) only validates; false restarts", required = false) Boolean dryRun) {
        JobExecution e = repository.getJobExecution(executionId);
        if (e == null) return "REFUSED: no job execution " + executionId;
        String job = e.getJobInstance().getJobName();
        List<JobExecution> ofInstance = repository.getJobExecutions(e.getJobInstance());
        long latest = ofInstance.stream().mapToLong(JobExecution::getId).max().orElse(executionId);
        List<String> violations = new ArrayList<>();
        if (!restartable.contains(job)) violations.add("job " + job + " is not on the restart allow-list");
        if (e.getStatus() != BatchStatus.FAILED) violations.add("execution status is " + e.getStatus() + ", not FAILED");
        if (latest != executionId) violations.add("execution " + latest + " is newer for the same instance");
        if (!repository.findRunningJobExecutions(job).isEmpty()) violations.add("another execution of " + job + " is running");
        if (reason == null || reason.strip().length() < 15) violations.add("a reason of at least 15 characters is required");
        if (ofInstance.size() - 1 >= maxRestarts) violations.add("instance already restarted " + (ofInstance.size() - 1) + " times (max " + maxRestarts + ")");
        if (!violations.isEmpty()) return "REFUSED: " + String.join("; ", violations);
        if (dryRun == null || dryRun) return "DRY RUN OK: execution " + executionId + " of " + job + " can be restarted; call again with dryRun=false";
        try {
            JobExecution restarted = operator.restart(e);
            return "RESTARTED: new execution " + restarted.getId() + " finished " + restarted.getStatus() + " (reason: " + reason.strip() + ")";
        } catch (Exception ex) {
            return "FAILED TO RESTART: " + ex.getMessage();
        }
    }
}
