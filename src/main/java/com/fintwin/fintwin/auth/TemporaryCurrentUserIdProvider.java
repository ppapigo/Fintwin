package com.fintwin.fintwin.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TemporaryCurrentUserIdProvider implements CurrentUserIdProvider {
    private final Long temporaryUserId;

    public TemporaryCurrentUserIdProvider(@Value("${fintwin.auth.temporary-user-id}") Long temporaryUserId) {
        this.temporaryUserId = temporaryUserId;
    }

    @Override
    public Long currentUserId() {
        return temporaryUserId;
    }
}
