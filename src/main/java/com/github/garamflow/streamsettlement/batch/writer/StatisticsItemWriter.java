package com.github.garamflow.streamsettlement.batch.writer;

import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.repository.statistics.ContentStatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 처리된 통계 데이터를 DB에 저장하는 Writer 구현
 * - 청크 단위로 집계된 통계 데이터를 일괄 저장
 */
@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class StatisticsItemWriter implements ItemWriter<ContentStatistics> {
    private final ContentStatisticsRepository contentStatisticsRepository;

    /**
     * 통계 데이터 저장 로직
     * - 청크 단위로 모아진 통계 데이터를 벌크 인서트
     * - 타입 캐스팅 후 리스트로 변환하여 일괄 저장
     */
    @Override
    public void write(@NonNull Chunk<? extends ContentStatistics> chunk) {
        List<ContentStatistics> statistics = chunk.getItems().stream()
                .map(item -> (ContentStatistics) item)
                .toList();
        contentStatisticsRepository.bulkInsert(statistics);
    }
}