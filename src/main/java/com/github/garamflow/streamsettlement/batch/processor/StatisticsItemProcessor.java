package com.github.garamflow.streamsettlement.batch.processor;

import com.github.garamflow.streamsettlement.batch.dto.CumulativeStatisticsDto;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import com.github.garamflow.streamsettlement.repository.stream.ContentPostRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;


@Component
@StepScope
@RequiredArgsConstructor
@Slf4j
public class StatisticsItemProcessor implements ItemProcessor<CumulativeStatisticsDto, ContentStatistics> {

    private final ContentPostRepository contentPostRepository;
    private final Map<Long, ContentPost> contentPostCache = new ConcurrentHashMap<>(1000);


    @Value("#{jobParameters['targetDate']}")
    private LocalDate targetDate;

    @PostConstruct
    public void init() {
        log.info("Initializing content post cache...");
        contentPostRepository.findAll()
                .forEach(content -> {
                    contentPostCache.put(content.getId(), content);
                    log.debug("Cached content: id={}, totalViews={}", 
                            content.getId(), content.getTotalViews());
                });
        log.info("Content post cache initialized with {} items", contentPostCache.size());
    }

    @Override
    public ContentStatistics process(@NonNull CumulativeStatisticsDto dto) {
        ContentPost contentPost = contentPostCache.computeIfAbsent(dto.contentId(), id -> {
            log.debug("Cache miss for content id: {}. Loading from database...", id);
            return contentPostRepository.findById(id)
                    .orElseThrow(() -> new NoSuchElementException(
                            String.format("ContentPost not found in cache or database for id: %d", id)));
        });

        return ContentStatistics.existingBuilder()
                .contentPost(contentPost)
                .statisticsDate(targetDate)
                .period(StatisticsPeriod.DAILY)
                .viewCount(dto.totalViews())
                .watchTime(dto.totalWatchTime())
                .accumulatedViews(contentPost.getTotalViews())
                .build();
    }
}

