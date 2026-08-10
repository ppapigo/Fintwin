package com.fintwin.fintwin.agent.domain;

public enum AgentState {
    RECEIVED,
    ROUTED,
    GAP_CHECKED,
    TOOL_EXECUTING,
    TOOL_COMPLETED,
    RISK_CHECKED,
    EXPLAINED,
    COMPLETED,
    NEEDS_INPUT,
    REJECTED,
    FAILED
}
