package com.github.garamflow.streamsettlement.entity.member;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long id;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Column(name = "username")
    private String username;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "provider")
    private String provider;  // oauth2 제공자 (google, naver 등)

    @Column(name = "provider_id")
    private String providerId;  // oauth2 제공자의 고유 id

    private Member(Builder builder) {
        this.email = builder.email;
        this.username = builder.username;
        this.role = builder.role;
        this.provider = builder.provider;
        this.providerId = builder.providerId;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Member updateEmail(String email) {
        this.email = email;
        return this;
    }

    public static class Builder {
        private String email;
        private String username;
        private Role role;
        private String provider;
        private String providerId;

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder role(Role role) {
            this.role = role;
            return this;
        }

        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }

        public Builder providerId(String providerId) {
            this.providerId = providerId;
            return this;
        }

        public Member build() {
            validateEmail();
            return new Member(this);
        }

        private void validateEmail() {
            if (email == null || email.isBlank()) {
                throw new IllegalArgumentException("Email is a required field");
            }
        }
    }
}

