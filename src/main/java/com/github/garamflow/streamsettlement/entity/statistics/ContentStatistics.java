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
@Table(name = "content_statistics",
indexes = {
  @Index(name = "idx_content_statistics_id_date", 
         columnList = "content_statistics_id, statistics_date"),
  @Index(name = "idx_content_statistics_composite", 
         columnList = "content_post_id, period, statistics_date")
})
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
    private StatisticsPeriod period;

    @Column(name = "view_count", nullable = false)
    private Long viewCount = 0L;

    @Column(name = "watch_time", nullable = false)
    private Long watchTime = 0L;

    @Column(name = "accumulated_views", nullable = false)
    private Long accumulatedViews = 0L;

    @Builder(builderMethodName = "createBuilder")
    private ContentStatistics(ContentPost contentPost, 
                            LocalDate statisticsDate,
                            StatisticsPeriod period, 
                            Long viewCount, 
                            Long watchTime) {
        this.contentPost = contentPost;
        this.statisticsDate = statisticsDate;
        this.period = period;
        this.viewCount = viewCount != null ? viewCount : 0L;
        this.watchTime = watchTime != null ? watchTime : 0L;
        this.accumulatedViews = Optional.ofNullable(contentPost.getTotalViews()).orElse(0L);
    }

    @Builder(builderMethodName = "existingBuilder")
    private ContentStatistics(Long id,
                            ContentPost contentPost,
                            LocalDate statisticsDate,
                            StatisticsPeriod period,
                            Long viewCount,
                            Long watchTime,
                            Long accumulatedViews) {
        this.id = id;
        this.contentPost = contentPost;
        this.statisticsDate = statisticsDate;
        this.period = period;
        this.viewCount = viewCount;
        this.watchTime = watchTime;
        this.accumulatedViews = accumulatedViews;
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
}
