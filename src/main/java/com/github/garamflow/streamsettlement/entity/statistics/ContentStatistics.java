package com.github.garamflow.streamsettlement.entity.statistics;

import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Optional;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "content_statistics")
public class ContentStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "content_statistics_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_post_id", nullable = false)
    private ContentPost contentPost;

    @Column(name = "statistics_date", nullable = false)
    private LocalDate statisticsDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "period", nullable = false)
    private StatisticsPeriod period; // DAILY, WEEKLY, MONTHLY, YEARLY

    @Column(name = "view_count", nullable = false)
    private Long viewCount = 0L;

    @Column(name = "watch_time", nullable = false)
    private Long watchTime = 0L;

    @Column(name = "accumulated_views", nullable = false)
    private Long accumulatedViews = 0L;

    @Builder(builderMethodName = "customBuilder")
    private ContentStatistics(ContentPost contentPost, LocalDate statisticsDate,
                              StatisticsPeriod period, Long viewCount, Long watchTime,
                              Long accumulatedViews) {
        this.contentPost = contentPost;
        this.statisticsDate = statisticsDate;
        this.period = period;
        this.viewCount = viewCount != null ? viewCount : 0L;
        this.watchTime = watchTime != null ? watchTime : 0L;
        this.accumulatedViews = accumulatedViews != null ? 
                               accumulatedViews : 
                               Optional.ofNullable(contentPost.getTotalViews()).orElse(0L);
    }

    @Override
    public String toString() {
        return String.format(
                "ContentStatistics(id=%d, contentPostId=%d, period=%s, statisticsDate=%s, viewCount=%d, watchTime=%d)",
                id,
                contentPost.getId(),
                period,
                statisticsDate,
                viewCount,
                watchTime
        );
    }

    public void addDailyStats(long additionalViews, long additionalWatchTime) {
        this.viewCount += additionalViews;
        this.watchTime += additionalWatchTime;
        this.accumulatedViews = this.contentPost.getTotalViews();
    }

    public void incrementViewCount() {
        this.viewCount++;
    }

    public void addWatchTime(Long watchTime) {
        this.watchTime += watchTime;
    }
}
