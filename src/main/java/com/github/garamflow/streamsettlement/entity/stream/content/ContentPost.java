package com.github.garamflow.streamsettlement.entity.stream.content;

import com.github.garamflow.streamsettlement.entity.stream.mapping.AdvertisementContentPost;
import com.github.garamflow.streamsettlement.entity.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentPost {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    private String description;

    // 영상 길이
    private Integer duration;

    // 영상 게시글 누적 조회수
    private Long totalViews;

    // 영상 게시글 누적 시청 시간
    private Long totalWatchTime;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 영상 게시글 주소
    @Column(nullable = false)
    private String url;

    @OneToMany(mappedBy = "contentPost")
    private final List<AdvertisementContentPost> contentPosts = new ArrayList<>();

    // 현재는 사용하지 않는 것들
    @Enumerated(EnumType.STRING)
    private ContentStatus status; // ACTIVE, INACTIVE, DELETED
    private String thumbnailUrl;
    private String tags;
    private String category;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void incrementTotalViews() {
        this.totalViews++;
    }

    public void addWatchTime(Long watchTime) {
        this.totalWatchTime += watchTime;
    }

    private ContentPost(Builder builder) {
        this.user = builder.user;
        this.title = builder.title;
        this.description = builder.description;
        this.duration = builder.duration;
        this.totalViews = builder.totalViews != null ? builder.totalViews : 0L;
        this.totalWatchTime = builder.totalWatchTime != null ? builder.totalWatchTime : 0L;
        this.url = builder.url;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public static class Builder {
        private User user;
        private String title;
        private String description;
        private Integer duration;
        private Long totalViews;
        private Long totalWatchTime;
        private String url;

        public Builder user(User user) {
            this.user = user;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder duration(Integer duration) {
            this.duration = duration;
            return this;
        }

        public Builder totalViews(Long totalViews) {
            this.totalViews = totalViews;
            return this;
        }

        public Builder totalWatchTime(Long totalWatchTime) {
            this.totalWatchTime = totalWatchTime;
            return this;
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public ContentPost build() {
            validateRequiredFields();
            return new ContentPost(this);
        }

        private void validateRequiredFields() {
            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException("Title is required");
            }
            if (url == null || url.isBlank()) {
                throw new IllegalArgumentException("URL is required");
            }
        }
    }
}
