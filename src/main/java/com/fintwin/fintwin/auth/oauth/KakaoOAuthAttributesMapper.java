package com.fintwin.fintwin.auth.oauth;

import com.fintwin.fintwin.user.domain.OAuthProvider;

import java.math.BigInteger;
import java.util.Map;

public final class KakaoOAuthAttributesMapper {
    public MappedOAuthIdentity map(Map<String, Object> attributes) {
        Object rawId = attributes == null ? null : attributes.get("id");
        String subject = normalizeId(rawId);
        if (subject == null || subject.length() > 255) {
            throw new OAuthAttributeMappingException("invalid_kakao_id");
        }
        return new MappedOAuthIdentity(OAuthProvider.KAKAO, subject);
    }

    private String normalizeId(Object rawId) {
        if (rawId instanceof Byte || rawId instanceof Short || rawId instanceof Integer
                || rawId instanceof Long || rawId instanceof BigInteger) {
            String value = rawId.toString();
            return value.matches("[0-9]+") ? value : null;
        }
        if (rawId instanceof String value && value.matches("[0-9]+")) {
            return value;
        }
        return null;
    }
}
