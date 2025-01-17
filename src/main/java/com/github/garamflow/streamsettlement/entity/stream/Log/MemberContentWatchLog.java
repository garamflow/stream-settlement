package com.github.garamflow.streamsettlement.entity.stream.Log;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberContentWatchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private Long contentPostId;

    @Column(name = "last_playback_position", nullable = false)
    private Long lastPlaybackPosition;

    @Column(name = "total_playback_time", nullable = false)
    private Long totalPlaybackTime;

    @Column(name = "watched_date", nullable = false)
    private LocalDate watchedDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "streaming_status", nullable = false)
    private StreamingStatus streamingStatus;

    // 유효성 검사 메서드 추가
    public boolean isTotalPlaybackTimeValid() {
        return totalPlaybackTime <= lastPlaybackPosition;
    }

    public boolean isWatchDateValid() {
        return !watchedDate.isAfter(LocalDate.now());
    }

    // 새로운 시청 로그 생성용
    @Builder(builderMethodName = "createBuilder")
    private MemberContentWatchLog(Long memberId,
                                  Long contentPostId,
                                  Long lastPlaybackPosition,
                                  StreamingStatus streamingStatus) {
        this.memberId = memberId;
        this.contentPostId = contentPostId;
        this.lastPlaybackPosition = lastPlaybackPosition != null ? lastPlaybackPosition : 0L;
        this.totalPlaybackTime = 0L;
        this.watchedDate = LocalDate.now();
        this.streamingStatus = streamingStatus != null ? streamingStatus : StreamingStatus.IN_PROGRESS;
    }

    // 기존 시청 로그 조회용
    @Builder(builderMethodName = "existingBuilder")
    private MemberContentWatchLog(Long id,
                                  Long memberId,
                                  Long contentPostId,
                                  Long lastPlaybackPosition,
                                  Long totalPlaybackTime,
                                  LocalDate watchedDate,
                                  LocalDateTime createdAt,
                                  StreamingStatus streamingStatus) {
        this.id = id;
        this.memberId = memberId;
        this.contentPostId = contentPostId;
        this.lastPlaybackPosition = lastPlaybackPosition;
        this.totalPlaybackTime = totalPlaybackTime;
        this.watchedDate = watchedDate;
        this.createdAt = createdAt;
        this.streamingStatus = streamingStatus;
    }

    public void updatePlaybackPosition(Long newPosition) {
        if (newPosition < 0) {
            throw new IllegalArgumentException("Position cannot be negative");
        }
        if (newPosition < this.lastPlaybackPosition) {
            throw new IllegalArgumentException("New position cannot be less than current position");
        }
        this.lastPlaybackPosition = newPosition;
    }

    public void updateTotalPlaybackTime(Long additionalTime) {
        if (additionalTime < 0) {
            throw new IllegalArgumentException("Additional time cannot be negative");
        }
        this.totalPlaybackTime += additionalTime;
    }

    public void updateStatus(StreamingStatus newStatus) {
        this.streamingStatus = newStatus;
    }
}
