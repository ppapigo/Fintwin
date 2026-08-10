package com.fintwin.fintwin.agent.routing;

import com.fintwin.fintwin.agent.domain.AgentIntent;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public final class DeterministicIntentRouter {
    public Optional<AgentRoute> route(String requestedIntent) {
        if (requestedIntent == null) {
            return Optional.empty();
        }
        return switch (requestedIntent) {
            case "BASELINE_SIMULATION" -> Optional.of(new AgentRoute(AgentIntent.BASELINE_SIMULATION,
                    AgentToolName.BASELINE_SIMULATION_TOOL));
            case "WHAT_IF_SIMULATION" -> Optional.of(new AgentRoute(AgentIntent.WHAT_IF_SIMULATION,
                    AgentToolName.SCENARIO_COMPARISON_TOOL));
            case "GOAL_REVERSE_SIMULATION" -> Optional.of(new AgentRoute(AgentIntent.GOAL_REVERSE_SIMULATION,
                    AgentToolName.GOAL_REVERSE_SIMULATION_TOOL));
            default -> Optional.empty();
        };
    }
}
