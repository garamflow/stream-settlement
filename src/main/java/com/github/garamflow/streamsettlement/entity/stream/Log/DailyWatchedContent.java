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

    @Builder(builderMethodName = "customBuilder")
    public DailyWatchedContent(Long contentPostId, LocalDate watchedDate) {
        this.contentPostId = contentPostId;
        this.watchedDate = watchedDate != null ? watchedDate : LocalDate.now();
    }
}
