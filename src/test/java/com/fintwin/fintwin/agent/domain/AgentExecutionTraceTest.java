package com.fintwin.fintwin.agent.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentExecutionTraceTest {
    @Test
    void supportsTheNormalStatePath() {
        AgentExecutionTrace trace = AgentExecutionTrace.start();
        trace.transitionTo(AgentState.ROUTED, "router", "ROUTED");
        trace.transitionTo(AgentState.GAP_CHECKED, "gap", "COMPLETE");
        trace.transitionTo(AgentState.TOOL_EXECUTING, "tool", "STARTED");
        trace.transitionTo(AgentState.TOOL_COMPLETED, "tool", "COMPLETED");
        trace.transitionTo(AgentState.RISK_CHECKED, "risk", "CHECKED");
        trace.transitionTo(AgentState.EXPLAINED, "explanation", "COMPOSED");
        trace.transitionTo(AgentState.COMPLETED, "orchestrator", "COMPLETED");

        assertThat(trace.steps()).extracting(AgentTraceStep::state)
                .containsExactly(AgentState.RECEIVED, AgentState.ROUTED, AgentState.GAP_CHECKED,
                        AgentState.TOOL_EXECUTING, AgentState.TOOL_COMPLETED, AgentState.RISK_CHECKED,
                        AgentState.EXPLAINED, AgentState.COMPLETED);
    }

    @Test
    void blocksInvalidAndPostTerminalTransitions() {
        AgentExecutionTrace invalid = AgentExecutionTrace.start();
        assertThatThrownBy(() -> invalid.transitionTo(AgentState.TOOL_EXECUTING, "tool", "STARTED"))
                .isInstanceOf(IllegalStateException.class);

        AgentExecutionTrace rejected = AgentExecutionTrace.start();
        rejected.transitionTo(AgentState.REJECTED, "router", "REJECTED");
        assertThatThrownBy(() -> rejected.transitionTo(AgentState.ROUTED, "router", "ROUTED"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void supportsNeedsInputAndFailedPaths() {
        AgentExecutionTrace needsInput = AgentExecutionTrace.start();
        needsInput.transitionTo(AgentState.ROUTED, "router", "ROUTED");
        needsInput.transitionTo(AgentState.GAP_CHECKED, "gap", "MISSING");
        needsInput.transitionTo(AgentState.NEEDS_INPUT, "orchestrator", "NEEDS_INPUT");
        assertThat(needsInput.currentState()).isEqualTo(AgentState.NEEDS_INPUT);

        AgentExecutionTrace failed = AgentExecutionTrace.start();
        failed.transitionTo(AgentState.ROUTED, "router", "ROUTED");
        failed.transitionTo(AgentState.GAP_CHECKED, "gap", "COMPLETE");
        failed.transitionTo(AgentState.TOOL_EXECUTING, "tool", "STARTED");
        failed.transitionTo(AgentState.FAILED, "tool", "FAILED");
        assertThat(failed.currentState()).isEqualTo(AgentState.FAILED);
    }
}
