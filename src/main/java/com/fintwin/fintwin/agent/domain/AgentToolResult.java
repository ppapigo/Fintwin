package com.fintwin.fintwin.agent.domain;

public sealed interface AgentToolResult permits BaselineAgentToolResult, ScenarioAgentToolResult,
        GoalAgentToolResult {
    AgentResultType resultType();
}
