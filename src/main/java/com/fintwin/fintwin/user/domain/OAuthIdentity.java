package com.fintwin.fintwin.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Collate;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "oauth_identities",
        uniqueConstraints = @UniqueConstraint(name = "uk_oauth_identity_provider_subject",
                columnNames = {"provider", "provider_subject"}),
        indexes = @Index(name = "idx_oauth_identity_user", columnList = "user_id"))
public class OAuthIdentity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private OAuthProvider provider;

    @Collate("utf8mb4_0900_bin")
    @Column(name = "provider_subject", nullable = false, length = 255, updatable = false)
    private String providerSubject;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_login_at", nullable = false)
    private Instant lastLoginAt;

    protected OAuthIdentity() {
    }

    private OAuthIdentity(User user, OAuthProvider provider, String providerSubject) {
        this.user = Objects.requireNonNull(user);
        this.provider = Objects.requireNonNull(provider);
        this.providerSubject = requireSubject(providerSubject);
    }

    public static OAuthIdentity create(User user, OAuthProvider provider, String providerSubject) {
        return new OAuthIdentity(user, provider, providerSubject);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        lastLoginAt = now;
    }

    public void recordLogin() {
        lastLoginAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public OAuthProvider getProvider() {
        return provider;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    @Override
    public String toString() {
        return "OAuthIdentity[provider=" + provider + ", providerSubject=[REDACTED]]";
    }

    private static String requireSubject(String value) {
        if (value == null || value.isBlank() || !value.equals(value.strip()) || value.length() > 255
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("OAuth provider subject is invalid");
        }
        return value;
    }
}
