package io.github.aaronsamuvel.blueprints.hitl.submission;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import java.util.List;
import java.util.Map;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.hook.NodeHook;
import org.springframework.ai.chat.client.ChatClient;

/**
 * The handbook's Blueprint 2 graph:
 * <pre>
 * START -> parseSubmissionNode -+-> skillsAndArchitectureNode  -+
 *                               +-> experienceAndResilienceNode -+-> consensusAggregatorNode --AUTO_APPROVE----> autoApproveNode --> END
 *                               +-> cultureAndGovernanceNode    -+                           --AUTO_REJECT-----> autoRejectNode  --> END
 *                               +-> redFlagAuditNode            -+                           --HUMAN_ESCALATION-> humanDecisionNode (INTERRUPT) --> END
 * </pre>
 * The four evaluators are parallel branches; the caller passes a virtual-thread executor for them
 * with {@code RunnableConfig.Builder.addParallelNodeExecutor(PARSE, executor)}.
 */
public final class SubmissionReviewGraph {

    public static final String PARSE = "parseSubmissionNode";
    public static final String SKILLS = "skillsAndArchitectureNode";
    public static final String EXPERIENCE = "experienceAndResilienceNode";
    public static final String CULTURE = "cultureAndGovernanceNode";
    public static final String RED_FLAGS = "redFlagAuditNode";
    public static final String CONSENSUS = "consensusAggregatorNode";
    public static final String AUTO_APPROVE = "autoApproveNode";
    public static final String AUTO_REJECT = "autoRejectNode";
    public static final String HUMAN = "humanDecisionNode";

    private SubmissionReviewGraph() {}

    public static CompiledGraph<ReviewWorkflowState> build(ChatClient chat, BaseCheckpointSaver saver,
                                                           NodeHook.WrapCall<ReviewWorkflowState> hook) throws GraphStateException {
        StateGraph<ReviewWorkflowState> g = new StateGraph<>(ReviewWorkflowState.SCHEMA, ReviewWorkflowState::new)
                .addNode(PARSE, node_async(new ParseSubmissionNode(chat)))
                .addNode(SKILLS, node_async(new SkillsAndArchitectureNode(chat)))
                .addNode(EXPERIENCE, node_async(new ExperienceAndResilienceNode(chat)))
                .addNode(CULTURE, node_async(new CultureAndGovernanceNode(chat)))
                .addNode(RED_FLAGS, node_async(new RedFlagAuditNode(chat)))
                .addNode(CONSENSUS, node_async(new ConsensusAggregatorNode()))
                .addNode(AUTO_APPROVE, node_async(s -> Map.of(ReviewWorkflowState.STATUS, "AUTO_APPROVED",
                        ReviewWorkflowState.NODE_LOG, List.of("autoApprove"))))
                .addNode(AUTO_REJECT, node_async(s -> Map.of(ReviewWorkflowState.STATUS, "AUTO_REJECTED",
                        ReviewWorkflowState.NODE_LOG, List.of("autoReject"))))
                .addNode(HUMAN, node_async(s -> {
                    HumanVerdict v = s.humanVerdict().orElseThrow(() ->
                            new IllegalStateException("humanDecisionNode resumed without a humanVerdict in the state"));
                    return Map.of(ReviewWorkflowState.STATUS, v.approved() ? "HUMAN_APPROVED" : "HUMAN_REJECTED",
                            ReviewWorkflowState.NODE_LOG, List.of("humanDecision by " + v.reviewer() + ": " + (v.approved() ? "approve" : "reject")));
                }))
                .addEdge(START, PARSE);
        for (String evaluator : List.of(SKILLS, EXPERIENCE, CULTURE, RED_FLAGS)) {
            g.addEdge(PARSE, evaluator).addEdge(evaluator, CONSENSUS);
        }
        g.addConditionalEdges(CONSENSUS,
                        edge_async(s -> s.decision().orElse(RoutingDecision.HUMAN_ESCALATION).name()),
                        Map.of(RoutingDecision.AUTO_APPROVE.name(), AUTO_APPROVE,
                                RoutingDecision.AUTO_REJECT.name(), AUTO_REJECT,
                                RoutingDecision.HUMAN_ESCALATION.name(), HUMAN))
                .addEdge(AUTO_APPROVE, END)
                .addEdge(AUTO_REJECT, END)
                .addEdge(HUMAN, END);
        if (hook != null) g.addWrapCallNodeHook(hook);
        return g.compile(CompileConfig.builder()
                .checkpointSaver(saver)
                .interruptBefore(HUMAN)
                .releaseThread(false)
                .build());
    }
}
