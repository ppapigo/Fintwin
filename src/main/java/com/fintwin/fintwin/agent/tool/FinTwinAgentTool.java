package com.fintwin.fintwin.agent.tool;

import com.fintwin.fintwin.agent.domain.AgentCommand;
import com.fintwin.fintwin.agent.domain.AgentToolResult;
import com.fintwin.fintwin.agent.routing.AgentToolName;

public interface FinTwinAgentTool {
    AgentToolName name();

    AgentToolResult execute(Long currentUserId, AgentCommand command);
}
