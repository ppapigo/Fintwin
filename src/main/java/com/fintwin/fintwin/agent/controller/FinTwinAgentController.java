package com.fintwin.fintwin.agent.controller;

import com.fintwin.fintwin.agent.dto.AgentExecutionRequest;
import com.fintwin.fintwin.agent.dto.AgentExecutionResponse;
import com.fintwin.fintwin.agent.orchestration.FinTwinAgentOrchestrator;
import com.fintwin.fintwin.auth.CurrentUserIdProvider;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public final class FinTwinAgentController {
    private final FinTwinAgentOrchestrator orchestrator;
    private final CurrentUserIdProvider currentUserIdProvider;

    public FinTwinAgentController(FinTwinAgentOrchestrator orchestrator,
                                  CurrentUserIdProvider currentUserIdProvider) {
        this.orchestrator = orchestrator;
        this.currentUserIdProvider = currentUserIdProvider;
    }

    @PostMapping("/execute")
    public AgentExecutionResponse execute(@Valid @RequestBody AgentExecutionRequest request) {
        return AgentExecutionResponse.from(orchestrator.execute(currentUserIdProvider.currentUserId(),
                request.toDomain()));
    }
}
