package com.github.garamflow.streamsettlement.entity.stream.Log;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyWatchedContent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long contentPostId;

    @Column(nullable = false)
    private LocalDate watchedDate;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @Builder(builderMethodName = "createBuilder")
    public DailyWatchedContent(Long contentPostId) {
        this.contentPostId = contentPostId;
        this.watchedDate = LocalDate.now();
    }

    @Builder(builderMethodName = "existingBuilder")
    public DailyWatchedContent(Long id,
                              Long contentPostId,
                              LocalDate watchedDate,
                              LocalDateTime createdAt) {
        this.id = id;
        this.contentPostId = contentPostId;
        this.watchedDate = watchedDate;
        this.createdAt = createdAt;
    }
}
