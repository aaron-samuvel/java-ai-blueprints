package io.github.aaronsamuvel.blueprints.hitl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import io.github.aaronsamuvel.blueprints.eval.EvaluationGate;
import io.github.aaronsamuvel.blueprints.eval.LexicalJudgeChatModel;
import io.github.aaronsamuvel.blueprints.hitl.ReviewWorkflow.ReviewView;
import io.github.aaronsamuvel.blueprints.shared.StubChatModel;

import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.junit.jupiter.api.Test;

class ReviewWorkflowTest {

    static final String CONTEXT = "Customers can request a full refund within 30 days of delivery.";
    static final String GOOD = "You can request a full refund within 30 days of delivery.";
    static final String BAD = "You can get a refund within 90 days, plus a free gift card.";

    private static StubChatModel drafts(String... replies) {
        Deque<String> q = new ArrayDeque<>(List.of(replies));
        return new StubChatModel(p -> q.isEmpty() ? GOOD : q.poll());
    }

    private static ReviewWorkflow workflow(StubChatModel drafter) throws Exception {
        return new ReviewWorkflow(ReviewGraph.build(drafter, new EvaluationGate(new LexicalJudgeChatModel()), new MemorySaver(), 2));
    }

    @Test
    void goodDraftPausesForHumanThenPublishesOnApproval() throws Exception {
        ReviewWorkflow wf = workflow(drafts(GOOD));
        ReviewView v = wf.start("How long do I have for a refund?", CONTEXT);

        assertThat(v.status()).isEqualTo("AWAITING_HUMAN");
        assertThat(v.awaitingHuman()).isTrue();
        assertThat(v.evalPassed()).isTrue();
        assertThat(v.draft()).isEqualTo(GOOD);
        assertThat(v.history()).containsExactly("drafter: revision 0", "evaluator: pass");

        ReviewView done = wf.decide(v.threadId(), true, null);
        assertThat(done.status()).isEqualTo("PUBLISHED");
        assertThat(done.awaitingHuman()).isFalse();
        assertThat(done.history()).endsWith("human: approve", "published");
    }

    @Test
    void evaluatorSendsHallucinatedDraftBackWithFeedbackBeforeAnyHumanSeesIt() throws Exception {
        StubChatModel drafter = drafts(BAD, GOOD);
        ReviewView v = workflow(drafter).start("How long do I have for a refund?", CONTEXT);

        assertThat(v.status()).isEqualTo("AWAITING_HUMAN");
        assertThat(v.revision()).isEqualTo(1);
        assertThat(v.draft()).isEqualTo(GOOD);
        assertThat(v.history()).containsExactly("drafter: revision 0", "evaluator: fail", "drafter: revision 1", "evaluator: pass");
        // the second drafter call received the evaluator's feedback and the bad draft
        assertThat(StubChatModel.lastText(drafter.received().get(1))).contains("Automatic evaluation failed").contains(BAD);
    }

    @Test
    void persistentlyBadDraftIsEscalatedToHumanAfterMaxRevisions() throws Exception {
        ReviewView v = workflow(drafts(BAD, BAD, BAD)).start("refund?", CONTEXT);
        assertThat(v.status()).isEqualTo("AWAITING_HUMAN");
        assertThat(v.evalPassed()).isFalse();
        assertThat(v.revision()).isEqualTo(2);
    }

    @Test
    void humanRejectionTriggersRevisionWithHumanComment() throws Exception {
        StubChatModel drafter = drafts(GOOD, GOOD);
        ReviewWorkflow wf = workflow(drafter);
        ReviewView v = wf.start("refund?", CONTEXT);

        ReviewView after = wf.decide(v.threadId(), false, "Mention it is a full refund.");
        assertThat(after.status()).isEqualTo("AWAITING_HUMAN");    // paused again for a second look
        assertThat(after.revision()).isEqualTo(1);
        assertThat(StubChatModel.lastText(drafter.received().get(1))).contains("Human reviewer: Mention it is a full refund.");

        assertThat(wf.decide(v.threadId(), true, null).status()).isEqualTo("PUBLISHED");
    }

    @Test
    void rejectionAtMaxRevisionsEndsRejected() throws Exception {
        ReviewWorkflow wf = workflow(drafts(BAD, BAD, BAD));
        ReviewView v = wf.start("refund?", CONTEXT);
        ReviewView end = wf.decide(v.threadId(), false, "still wrong");
        assertThat(end.status()).isEqualTo("REJECTED");
        assertThat(end.history()).endsWith("human: reject", "rejected");
    }

    @Test
    void threadsAreIsolatedAndFinishedThreadsCannotBeDecidedAgain() throws Exception {
        ReviewWorkflow wf = workflow(drafts(GOOD, GOOD));
        ReviewView a = wf.start("refund?", CONTEXT);
        ReviewView b = wf.start("refund again?", CONTEXT);
        assertThat(a.threadId()).isNotEqualTo(b.threadId());

        wf.decide(a.threadId(), true, null);
        assertThat(wf.view(b.threadId()).status()).isEqualTo("AWAITING_HUMAN");
        assertThatThrownBy(() -> wf.decide(a.threadId(), true, null)).isInstanceOf(IllegalStateException.class);
    }
}
