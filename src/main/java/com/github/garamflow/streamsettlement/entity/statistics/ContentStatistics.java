package com.github.garamflow.streamsettlement.entity.statistics;

import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

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

    @Column(name = "view_count")
    private Long viewCount;

    @Column(name = "watch_time")
    private Long watchTime;

    @Column(name = "accumulated_views")
    private Long accumulatedViews;

    @Builder(builderMethodName = "customBuilder")
    private ContentStatistics(ContentPost contentPost, LocalDate statisticsDate,
                              StatisticsPeriod period, Long viewCount, Long watchTime,
                              Long accumulatedViews) {
        this.contentPost = contentPost;
        this.statisticsDate = statisticsDate;
        this.period = period;
        this.viewCount = viewCount;
        this.watchTime = watchTime;
        this.accumulatedViews = accumulatedViews;
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
