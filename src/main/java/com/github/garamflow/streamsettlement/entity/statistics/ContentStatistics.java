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
public class ContentStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private ContentPost contentPost;

    private LocalDate statisticsDate;

    @Enumerated(EnumType.STRING)
    private StatisticsPeriod period; // DAILY, WEEKLY, MONTHLY, YEARLY

    private Long viewCount;
    private Long watchTime;

    public ContentStatistics(Builder builder) {
        this.contentPost = builder.contentPost;
        this.statisticsDate = builder.statisticsDate;
        this.period = builder.period;
        this.viewCount = builder.viewCount;
        this.watchTime = builder.watchTime;
    }

    public static class Builder {
        private ContentPost contentPost;
        private LocalDate statisticsDate;
        private StatisticsPeriod period;
        private Long viewCount;
        private Long watchTime;

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

        public ContentStatistics build() {
            return new ContentStatistics(this);
        }
    }
}
