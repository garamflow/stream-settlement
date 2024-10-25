package com.github.garamflow.streamsettlement.entity.stream.Log;

import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import com.github.garamflow.streamsettlement.entity.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyUserViewLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private ContentPost contentPost;

    // 마지막 시청 시점
    private Integer lastViewedPosition;

    // 광고 시청 횟수 (5분당 1회)
    private Integer lastAdViewCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private LocalDate logDate;

    @Enumerated(EnumType.STRING)
    private StreamingStatus status = StreamingStatus.IN_PROGRESS;

    public static DailyUserViewLog createNewLog(User user, ContentPost contentPost) {
        return new Builder()
                .user(user)
                .contentPost(contentPost)
                .lastViewedPosition(0)
                .lastAdViewCount(0)
                .build();
    }

    public static DailyUserViewLog createContinueLog(User user, ContentPost contentPost, Integer lastViewedPosition, Integer lastAdViewCount) {
        return new Builder()
                .user(user)
                .contentPost(contentPost)
                .lastViewedPosition(lastViewedPosition)
                .lastAdViewCount(lastAdViewCount)
                .build();
    }

    public void updatePosition(Integer positionInSeconds, StreamingStatus newStatus) {
        // 1. 잘못된 위치를 검증한다.
        validatePosition(positionInSeconds);

        // 2. 광고를 처리한다.
        processAdvertisements(positionInSeconds);

        // 3. 같은 날인지 체크한다.
        updateDateIfNeeded();

        // 4. 위차와 상태를 업데이트한다.
        this.lastViewedPosition = positionInSeconds;
        this.status = newStatus;
        this.updatedAt = LocalDateTime.now();
    }

    public DailyUserViewLog(Builder builder) {
        validateRequiredFields(builder);
        this.user = builder.user;
        this.contentPost = builder.contentPost;
        this.lastViewedPosition = builder.lastViewedPosition != null ? builder.lastViewedPosition : 0;
        this.lastAdViewCount = builder.lastAdViewCount != null ? builder.lastAdViewCount : 0;
        this.logDate = LocalDate.now();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    private void validateRequiredFields(Builder builder) {
        if (builder.user == null) {
            throw new IllegalArgumentException("User is required");
        }
        if (builder.contentPost == null) {
            throw new IllegalArgumentException("ContentPost is required");
        }
    }

    public static class Builder {
        private User user;
        private ContentPost contentPost;
        private Integer lastViewedPosition;
        private Integer lastAdViewCount;

        public Builder user(User user) {
            this.user = user;
            return this;
        }

        public Builder contentPost(ContentPost contentPost) {
            this.contentPost = contentPost;
            return this;
        }

        public Builder lastViewedPosition(Integer lastViewedPosition) {
            this.lastViewedPosition = lastViewedPosition;
            return this;
        }

        public Builder lastAdViewCount(Integer lastAdViewCount) {
            this.lastAdViewCount = lastAdViewCount;
            return this;
        }

        public DailyUserViewLog build() {
            return new DailyUserViewLog(this);
        }

    }

    // 공통으로 사용하는 검증 로직
    private void validatePosition(Integer position) {
        if (position < 0) {
            throw new IllegalArgumentException("Position cannot be negative");
        }
        if (position < lastViewedPosition) {
            throw new IllegalArgumentException("Position cannot be less than the last viewed position");
        }
    }

    private void processAdvertisements(Integer position) {
        int currentAdViewCount = position / 300;
        if (currentAdViewCount > lastAdViewCount) {
            int newAdViews = currentAdViewCount - lastAdViewCount;
            contentPost.getContentPosts().forEach(adContent ->
                    adContent.getAdvertisement().incrementViews(newAdViews));
            this.lastAdViewCount = currentAdViewCount;
        }
    }

    private void updateDateIfNeeded() {
        LocalDate today = LocalDate.now();
        if (!this.logDate.equals(today)) {
            this.logDate = today;
        }
    }
}

