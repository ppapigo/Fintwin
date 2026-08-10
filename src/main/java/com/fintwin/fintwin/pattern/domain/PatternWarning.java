package com.fintwin.fintwin.pattern.domain;

import java.util.Objects;

public record PatternWarning(PatternWarningCode code, String message) {
    public PatternWarning {
        Objects.requireNonNull(code);
        Objects.requireNonNull(message);
    }
}
