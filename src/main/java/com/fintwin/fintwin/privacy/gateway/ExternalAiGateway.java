package com.fintwin.fintwin.privacy.gateway;

import com.fintwin.fintwin.privacy.domain.ExternalAiScenarioDraft;
import com.fintwin.fintwin.privacy.domain.ExternalAiScenarioRequest;

public interface ExternalAiGateway {
    ExternalAiScenarioDraft extractScenarioEvents(ExternalAiScenarioRequest request);
}
