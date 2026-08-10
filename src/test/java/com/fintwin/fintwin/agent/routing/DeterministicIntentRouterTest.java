package com.fintwin.fintwin.agent.routing;

import com.fintwin.fintwin.agent.domain.AgentIntent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicIntentRouterTest {
    private final DeterministicIntentRouter router = new DeterministicIntentRouter();

    @Test
    void mapsOnlySupportedIntentsToFixedTools() {
        assertThat(router.route("BASELINE_SIMULATION")).contains(
                new AgentRoute(AgentIntent.BASELINE_SIMULATION, AgentToolName.BASELINE_SIMULATION_TOOL));
        assertThat(router.route("WHAT_IF_SIMULATION")).contains(
                new AgentRoute(AgentIntent.WHAT_IF_SIMULATION, AgentToolName.SCENARIO_COMPARISON_TOOL));
        assertThat(router.route("GOAL_REVERSE_SIMULATION")).contains(
                new AgentRoute(AgentIntent.GOAL_REVERSE_SIMULATION, AgentToolName.GOAL_REVERSE_SIMULATION_TOOL));
    }

    @Test
    void rejectsUnsupportedOrToolShapedUserStrings() {
        assertThat(router.route("INVESTMENT_ADVICE")).isEmpty();
        assertThat(router.route("BASELINE_SIMULATION_TOOL")).isEmpty();
        assertThat(router.route("com.fintwin.SomeTool")).isEmpty();
        assertThat(router.route(null)).isEmpty();
    }
}
