package com.github.garamflow.streamsettlement.entity.statistics;

import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "content_statistics",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "unique_content_statistics", // 제약 조건 이름
                        columnNames = {"content_post_id", "statistics_date", "period"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_content_stats_lookup",
                        columnList = "content_post_id,statistics_date,period"
                ),
                @Index(
                        name = "idx_content_stats_date_period",
                        columnList = "statistics_date,period"
                )
        }
)
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

    public static Builder builder() {
        return new Builder();
    }

    private ContentStatistics(Builder builder) {
        this.contentPost = builder.contentPost;
        this.statisticsDate = builder.statisticsDate;
        this.period = builder.period;
        this.viewCount = builder.viewCount;
        this.watchTime = builder.watchTime;
        this.accumulatedViews = builder.accumulatedViews;
    }

    public static class Builder {
        private ContentPost contentPost;
        private LocalDate statisticsDate;
        private StatisticsPeriod period;
        private Long viewCount;
        private Long watchTime;
        private Long accumulatedViews;

        public Builder() {
        }

        public Builder contentPost(ContentPost contentPost) {
            this.contentPost = contentPost;
            return this;
        }

        public Builder statisticsDate(LocalDate statisticsDate) {
            this.statisticsDate = statisticsDate;
            return this;
        }

        public Builder period(StatisticsPeriod period) {
            this.period = period;
            return this;
        }

        public Builder viewCount(Long viewCount) {
            this.viewCount = viewCount;
            return this;
        }

        public Builder watchTime(Long watchTime) {
            this.watchTime = watchTime;
            return this;
        }

        public Builder accumulatedViews(Long accumulatedViews) {
            this.accumulatedViews = accumulatedViews;
            return this;
        }

        public ContentStatistics build() {
            return new ContentStatistics(this);
        }
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
}
