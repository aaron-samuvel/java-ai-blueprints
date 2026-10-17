package io.github.aaronsamuvel.blueprints.hitl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

/** Graph state shared by the drafter, evaluator and human-review nodes. */
public class ReviewState extends AgentState {

    public static final String QUESTION = "question";
    public static final String CONTEXT = "context";
    public static final String DRAFT = "draft";
    public static final String REVISION = "revision";
    public static final String EVAL_PASSED = "evalPassed";
    public static final String FEEDBACK = "feedback";
    public static final String HUMAN_DECISION = "humanDecision";
    public static final String STATUS = "status";
    public static final String HISTORY = "history";

    /** {@code history} accumulates (appender channel); every other key is last-write-wins. */
    public static final Map<String, Channel<?>> SCHEMA = Map.of(HISTORY, Channels.appender(ArrayList::new));

    public ReviewState(Map<String, Object> init) {
        super(init);
    }

    public String question() { return value(QUESTION, ""); }
    public String context() { return value(CONTEXT, ""); }
    public String draft() { return value(DRAFT, ""); }
    public int revision() { return value(REVISION, 0); }
    public boolean evalPassed() { return value(EVAL_PASSED, false); }
    public String feedback() { return value(FEEDBACK, ""); }
    public String humanDecision() { return value(HUMAN_DECISION, ""); }
    public String status() { return value(STATUS, "RUNNING"); }
    public List<String> history() { return value(HISTORY, List.of()); }
}
