package com.fintwin.fintwin.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
    @Id
    private Long id;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public User(Long id) {
        this.id = id;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
