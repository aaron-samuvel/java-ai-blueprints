package io.github.aaronsamuvel.blueprints.mcp.ops;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * A small Spring Batch job for the introspection tools to look at: {@code loadLedger} then
 * {@code reconcile}. {@code reconcile} fails while the {@link UpstreamFeed} is marked broken,
 * so a run can fail and later be restarted once the feed is "fixed".
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "blueprints.mcp.toolset", havingValue = "ops")
public class LedgerReconciliationJobConfiguration {

    public static final String JOB = "ledgerReconciliationJob";

    /** Simulated upstream dependency of the reconcile step. */
    public static final class UpstreamFeed {
        private final AtomicBoolean broken = new AtomicBoolean();

        public void markBroken(boolean value) {
            broken.set(value);
        }

        boolean broken() {
            return broken.get();
        }
    }

    @Bean
    UpstreamFeed upstreamFeed() {
        return new UpstreamFeed();
    }

    @Bean
    Job ledgerReconciliationJob(JobRepository repo, PlatformTransactionManager tx, UpstreamFeed feed) {
        Step load = new StepBuilder("loadLedger", repo)
                .tasklet((contribution, ctx) -> {
                    contribution.incrementWriteCount(120);
                    return RepeatStatus.FINISHED;
                }, tx).build();
        Step reconcile = new StepBuilder("reconcile", repo)
                .tasklet((contribution, ctx) -> {
                    if (feed.broken()) {
                        throw new IllegalStateException("upstream ledger feed returned HTTP 503 for partition 7");
                    }
                    contribution.incrementWriteCount(120);
                    return RepeatStatus.FINISHED;
                }, tx).build();
        return new JobBuilder(JOB, repo).start(load).next(reconcile).build();
    }
}
