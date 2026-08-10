package com.fintwin.fintwin.agent.domain;

import java.util.ArrayList;
import java.util.List;

public final class AgentExecutionTrace {
    private final List<AgentTraceStep> steps = new ArrayList<>();
    private AgentState currentState;

    private AgentExecutionTrace() {
        currentState = AgentState.RECEIVED;
        steps.add(new AgentTraceStep(1, currentState, "FinTwinAgentOrchestrator", "REQUEST_ACCEPTED"));
    }

    public static AgentExecutionTrace start() {
        return new AgentExecutionTrace();
    }

    public void transitionTo(AgentState nextState, String component, String outcomeCode) {
        if (!isAllowed(currentState, nextState)) {
            throw new IllegalStateException("Agent state transition is not allowed: " + currentState + " -> "
                    + nextState);
        }
        currentState = nextState;
        steps.add(new AgentTraceStep(steps.size() + 1, nextState, component, outcomeCode));
    }

    public AgentState currentState() {
        return currentState;
    }

    public List<AgentTraceStep> steps() {
        return List.copyOf(steps);
    }

    private boolean isAllowed(AgentState current, AgentState next) {
        return switch (current) {
            case RECEIVED -> next == AgentState.ROUTED || next == AgentState.REJECTED;
            case ROUTED -> next == AgentState.GAP_CHECKED;
            case GAP_CHECKED -> next == AgentState.TOOL_EXECUTING || next == AgentState.NEEDS_INPUT;
            case TOOL_EXECUTING -> next == AgentState.TOOL_COMPLETED || next == AgentState.FAILED;
            case TOOL_COMPLETED -> next == AgentState.RISK_CHECKED;
            case RISK_CHECKED -> next == AgentState.EXPLAINED;
            case EXPLAINED -> next == AgentState.COMPLETED;
            case COMPLETED, NEEDS_INPUT, REJECTED, FAILED -> false;
        };
    }
}
