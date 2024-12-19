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

/**
 * 통계 데이터 처리기
 * - 일일 시청 통계를 ContentStatistics 엔티티로 변환
 * - 콘텐츠 정보를 캐시하여 DB 조회 최소화
 */
@Component
@StepScope
@RequiredArgsConstructor
@Slf4j
public class StatisticsItemProcessor implements ItemProcessor<CumulativeStatisticsDto, ContentStatistics> {

    private final ContentPostRepository contentPostRepository;
    // 콘텐츠 캐시 (ID -> ContentPost)
    private final Map<Long, ContentPost> contentPostCache = new ConcurrentHashMap<>(1000);

    // 처리 대상 날짜
    @Value("#{jobParameters['targetDate']}")
    private LocalDate targetDate;

    /**
     * 초기화: 콘텐츠 정보 캐싱
     * - 모든 콘텐츠를 미리 로드하여 캐시
     * - DB 조회 부하 감소를 위한 선처리
     */
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

    /**
     * 통계 데이터 처리
     * - 캐시된 콘텐츠 정보 조회 (없으면 DB에서 로드)
     * - ContentStatistics 엔티티 생성
     * 
     * @param dto 일일 시청 통계 데이터
     * @return 생성된 ContentStatistics 엔티티
     * @throws NoSuchElementException 콘텐츠를 찾을 수 없는 경우
     */
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

