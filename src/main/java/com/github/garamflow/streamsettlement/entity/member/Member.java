package com.github.garamflow.streamsettlement.entity.member;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "member")
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


    @Builder(builderMethodName = "createBuilder")
    private Member(String email, String username, Role role,
                   String provider, String providerId) {
        validateEmail(email);
        this.email = email;
        this.username = username;
        this.role = role;
        this.provider = provider;
        this.providerId = providerId;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @Builder(builderMethodName = "existingBuilder")
    private Member(Long id, String email, String username, Role role) {
        this.id = id;
        this.email = email;
        this.username = username;
        this.role = role;
    }

    private void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is a required field");
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Member updateEmail(String email) {
        this.email = email;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Member member = (Member) o;
        return Objects.equals(id, member.id) &&
                Objects.equals(email, member.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, email);
    }
}

