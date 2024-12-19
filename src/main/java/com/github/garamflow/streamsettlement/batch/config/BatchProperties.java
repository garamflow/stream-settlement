package com.github.garamflow.streamsettlement.batch.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 배치 작업 관련 설정값들을 관리하는 클래스
 * application.yml 또는 properties 파일의 'batch' prefix 설정을 매핑
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "batch")
public class BatchProperties {

    private int chunkSize = 1000;  // 기본값 1000
    private int gridSize = 8;      // 기본 파티션 수 (8코어 시스템 기준)
    private Pool pool = new Pool();
    private Reader reader = new Reader();


    /**
     * 스레드 풀 설정
     * - 병렬 처리를 위한 스레드 풀 크기 및 설정
     */
    @Getter
    @Setter
    public static class Pool {
        private int coreSize = 2;
        private int maxSize = 4;
        private int queueCapacity = 25;
        private String threadNamePrefix = "batch-";
    }

    /**
     * 데이터 읽기 관련 설정
     * - 메모리 사용량 제어 및 성능 최적화를 위한 설정
     */
    @Getter
    @Setter
    public static class Reader {
        private int queueCapacity = 5000;        // 큐 최대 용량
        private int maxFetchSize = 5000;         // 최대 fetch 크기
        private int minFetchSize = 100;          // 최소 fetch 크기
        private long backPressureDelay = 50L;    // 백프레셔 대기 시간 (ms)
        private int highMemoryThreshold = 80;    // 높은 메모리 사용량 기준 (%)
        private int mediumMemoryThreshold = 60;  // 중간 메모리 사용량 기준 (%)
    }
}