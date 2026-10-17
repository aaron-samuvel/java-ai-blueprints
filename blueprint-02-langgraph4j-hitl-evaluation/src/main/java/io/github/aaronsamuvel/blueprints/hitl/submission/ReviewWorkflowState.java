package io.github.aaronsamuvel.blueprints.hitl.submission;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

/**
 * Shared state of the submission review graph.
 * <ul>
 *   <li>{@code dimensionScores} uses a custom reducer: each evaluator's score is merged in by dimension,
 *       so a re-run evaluator replaces its old score instead of adding a duplicate;</li>
 *   <li>{@code redFlags}, {@code critiques} and {@code nodeLog} are appender channels, so the four
 *       parallel evaluators can all write to them in the same super-step.</li>
 * </ul>
 */
public class ReviewWorkflowState extends AgentState {

    public static final String SUBMISSION_ID = "submissionId";
    public static final String RAW_DOCUMENT_TEXT = "rawDocumentText";
    public static final String PARSED_PROFILE_JSON = "parsedProfileJson";
    public static final String DIMENSION_SCORES = "dimensionScores";
    public static final String RED_FLAGS = "redFlags";
    public static final String CRITIQUES = "critiques";
    public static final String COMPOSITE_SCORE = "compositeScore";
    public static final String SCORE_SPREAD = "scoreSpread";
    public static final String DECISION = "decision";
    public static final String DECISION_REASON = "decisionReason";
    public static final String HUMAN_VERDICT = "humanVerdict";
    public static final String STATUS = "status";
    public static final String NODE_LOG = "nodeLog";

    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            DIMENSION_SCORES, Channels.<List<DimensionScore>>base(ReviewWorkflowState::mergeByDimension, ArrayList::new),
            RED_FLAGS, Channels.<String>appender(ArrayList::new),
            CRITIQUES, Channels.<String>appender(ArrayList::new),
            NODE_LOG, Channels.<String>appender(ArrayList::new));

    public ReviewWorkflowState(Map<String, Object> initData) {
        super(initData);
    }

    /** The score reducer: last write per dimension wins, first-seen order is kept. */
    static List<DimensionScore> mergeByDimension(List<DimensionScore> current, List<DimensionScore> update) {
        Map<String, DimensionScore> byDimension = new LinkedHashMap<>();
        if (current != null) current.forEach(s -> byDimension.put(s.dimension(), s));
        if (update != null) update.forEach(s -> byDimension.put(s.dimension(), s));
        return new ArrayList<>(byDimension.values());
    }

    public String submissionId() { return this.<String>value(SUBMISSION_ID).orElse(""); }
    public String rawDocumentText() { return this.<String>value(RAW_DOCUMENT_TEXT).orElse(""); }
    public String parsedProfileJson() { return this.<String>value(PARSED_PROFILE_JSON).orElse("{}"); }
    public List<DimensionScore> dimensionScores() { return this.<List<DimensionScore>>value(DIMENSION_SCORES).orElse(List.of()); }
    public List<String> redFlags() { return this.<List<String>>value(RED_FLAGS).orElse(List.of()); }
    public List<String> critiques() { return this.<List<String>>value(CRITIQUES).orElse(List.of()); }
    public List<String> nodeLog() { return this.<List<String>>value(NODE_LOG).orElse(List.of()); }
    public double compositeScore() { return this.<Double>value(COMPOSITE_SCORE).orElse(0.0); }
    public int scoreSpread() { return this.<Integer>value(SCORE_SPREAD).orElse(0); }
    public Optional<RoutingDecision> decision() { return this.<String>value(DECISION).map(RoutingDecision::valueOf); }
    public String decisionReason() { return this.<String>value(DECISION_REASON).orElse(""); }
    public Optional<HumanVerdict> humanVerdict() { return this.value(HUMAN_VERDICT); }
    public String status() { return this.<String>value(STATUS).orElse("RUNNING"); }
}
