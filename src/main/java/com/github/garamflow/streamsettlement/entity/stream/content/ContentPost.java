package com.github.garamflow.streamsettlement.entity.stream.content;

import com.github.garamflow.streamsettlement.entity.member.Member;
import com.github.garamflow.streamsettlement.entity.stream.mapping.AdvertisementContentPost;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
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
@Table(name = "content_post")
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

    @Column(name = "duration")
    private Integer duration;

    @Column(name = "total_views")
    private Long totalViews;

    @Column(name = "total_watch_time")
    private Long totalWatchTime;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "url", nullable = false)
    private String url;

    @OneToMany(mappedBy = "contentPost")
    private final List<AdvertisementContentPost> contentPosts = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ContentStatus status;

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

    @Builder(builderMethodName = "createBuilder")
    private ContentPost(Member member, 
                       String title, 
                       String description,
                       Integer duration, 
                       String url, 
                       ContentStatus status) {
        this.member = member;
        this.title = title;
        this.description = description;
        this.duration = duration;
        this.url = url;
        this.status = status;
        this.totalViews = 0L;
        this.totalWatchTime = 0L;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @Builder(builderMethodName = "existingBuilder")
    private ContentPost(Long id,
                       Member member,
                       String title,
                       String description,
                       Integer duration,
                       Long totalViews,
                       Long totalWatchTime,
                       LocalDateTime createdAt,
                       String url,
                       ContentStatus status) {
        this.id = id;
        this.member = member;
        this.title = title;
        this.description = description;
        this.duration = duration;
        this.totalViews = totalViews;
        this.totalWatchTime = totalWatchTime;
        this.createdAt = createdAt;
        this.url = url;
        this.status = status;
        this.updatedAt = LocalDateTime.now();
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
