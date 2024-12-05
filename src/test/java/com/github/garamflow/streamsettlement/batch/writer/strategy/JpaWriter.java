package com.github.garamflow.streamsettlement.batch.writer.strategy;

import com.github.garamflow.streamsettlement.batch.performance.WriterStrategy;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class JpaWriter implements WriterStrategy {
    
    private final EntityManager entityManager;
    
    @Override
    @Transactional
    public void write(List<ContentStatistics> statistics) {
        for (ContentStatistics stat : statistics) {
            entityManager.persist(stat);
        }
        entityManager.flush();
        entityManager.clear();
    }
} 