package com.fintwin.fintwin.goal.domain;

import java.util.Objects;

public record GoalWarning(GoalWarningCode code, String message) {
    public GoalWarning {
        Objects.requireNonNull(code);
        Objects.requireNonNull(message);
    }
}
