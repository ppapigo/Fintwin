package com.fintwin.fintwin.agent.routing;

import com.fintwin.fintwin.agent.domain.AgentIntent;

public record AgentRoute(AgentIntent intent, AgentToolName toolName) {
}
