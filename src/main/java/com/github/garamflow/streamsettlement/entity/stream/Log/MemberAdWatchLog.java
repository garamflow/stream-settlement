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
public class MemberAdWatchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private Long contentPostId;

    @Column(nullable = false)
    private Long advertisementId;

    @Column(name = "playback_position")
    private Long playbackPosition;

    @Enumerated(EnumType.STRING)
    @Column(name = "streaming_status", nullable = false)
    private StreamingStatus streamingStatus = StreamingStatus.IN_PROGRESS;


    @Column(name = "watched_date", nullable = false)
    private LocalDate watchedDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder(builderMethodName = "customBuilder")
    public MemberAdWatchLog(Long memberId, Long contentPostId, Long advertisementId,
                            Long playbackPosition, LocalDate watchedDate, StreamingStatus streamingStatus) {
        this.memberId = memberId;
        this.contentPostId = contentPostId;
        this.advertisementId = advertisementId;
        this.playbackPosition = playbackPosition != null ? playbackPosition : 0L;
        this.watchedDate = watchedDate != null ? watchedDate : LocalDate.now();
        this.streamingStatus = streamingStatus != null ? streamingStatus : StreamingStatus.IN_PROGRESS;
    }

    public void updateStatus(StreamingStatus newStatus) {
        this.streamingStatus = newStatus;
    }

    public boolean isValidPlaybackPosition() {
        return playbackPosition >= 0;
    }

    public void updatePlaybackPosition(Long newPosition) {
        if (newPosition < 0) {
            throw new IllegalArgumentException("Position cannot be negative");
        }
        this.playbackPosition = newPosition;
    }
}
