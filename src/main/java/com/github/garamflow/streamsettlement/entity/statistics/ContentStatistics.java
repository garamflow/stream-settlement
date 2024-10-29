package com.github.garamflow.streamsettlement.entity.statistics;

import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Entity
public class ContentStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private ContentPost contentPost;

    private LocalDate statisticsDate;

    @Enumerated(EnumType.STRING)
    private StatisticsPeriod period; // DAILY, WEEKLY, MONTHLY, YEARLY

    private Long viewCount;
    private Long watchTime;
}
