package com.github.garamflow.streamsettlement.entity.stream.content;

import com.github.garamflow.streamsettlement.entity.stream.mapping.AdvertisementContentPost;
import com.github.garamflow.streamsettlement.entity.member.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "content_post",
    indexes = {
        @Index(
            name = "idx_content_member_status",
            columnList = "member_id,status"
        )
    }
)
public class ContentPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "content_post_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    // 영상 길이
    @Column(name = "duration")
    private Integer duration;

    // 영상 게시글 누적 조회수
    @Column(name = "total_views")
    private Long totalViews;

    // 영상 게시글 누적 시청 시간
    @Column(name="total_watch_time")
    private Long totalWatchTime;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 영상 게시글 주소
    @Column(name = "url",nullable = false)
    private String url;

    @OneToMany(mappedBy = "contentPost")
    private final List<AdvertisementContentPost> contentPosts = new ArrayList<>();

    // 현재는 사용하지 않는 것들
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ContentStatus status; // ACTIVE, INACTIVE, DELETED\
    @Column(name = "thumbnail_url")
    private String thumbnailUrl;
    @Column(name = "tags")
    private String tags;
    @Column(name = "category")
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
        this.member = builder.member;
        this.title = builder.title;
        this.description = builder.description;
        this.duration = builder.duration;
        this.totalViews = builder.totalViews != null ? builder.totalViews : 0L;
        this.totalWatchTime = builder.totalWatchTime != null ? builder.totalWatchTime : 0L;
        this.url = builder.url;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.status = builder.status;
    }

    public static class Builder {
        private Member member;
        private String title;
        private String description;
        private Integer duration;
        private Long totalViews;
        private Long totalWatchTime;
        private String url;
        private ContentStatus status;

        public Builder member(Member member) {
            this.member = member;
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

        public Builder status(ContentStatus status) {
            this.status = status;
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

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContentPost that = (ContentPost) o;
        return Objects.equals(id, that.id) && 
               Objects.equals(title, that.title) &&
               Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, url);
    }
}
