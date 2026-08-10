package com.fintwin.fintwin.auth.oauth;

import com.fintwin.fintwin.user.domain.OAuthProvider;

import java.util.Map;

public final class GoogleOAuthAttributesMapper {
    public MappedOAuthIdentity map(Map<String, Object> attributes) {
        Object rawSubject = attributes == null ? null : attributes.get("sub");
        if (!(rawSubject instanceof String subject) || !isValidSubject(subject)) {
            throw new OAuthAttributeMappingException("invalid_google_subject");
        }
        return new MappedOAuthIdentity(OAuthProvider.GOOGLE, subject);
    }

    private boolean isValidSubject(String subject) {
        return !subject.isBlank() && subject.equals(subject.strip()) && subject.length() <= 255
                && subject.codePoints().allMatch(codePoint -> !Character.isISOControl(codePoint));
    }
}
