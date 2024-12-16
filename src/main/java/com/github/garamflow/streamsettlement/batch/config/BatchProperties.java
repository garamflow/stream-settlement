package com.github.garamflow.streamsettlement.batch.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "batch")
public class BatchProperties {

    private int chunkSize = 1000;  // 기본값 1000
    private int maxGridSize = 8;    // 기본값 8
    private Partition partition = new Partition();
    private Pool pool = new Pool();
    private Reader reader = new Reader();

    @Getter
    @Setter
    public static class Partition {
        private long smallDataSize = 10000L;    // 10000개 미만
        private long mediumDataSize = 100000L;  // 100000개 미만
        private long largeDataSize = 1000000L;  // 1000000개 미만

        private int smallGridSize = 1;      // 작은 데이터용 파티션 수
        private int mediumGridSize = 2;     // 중간 데이터용 파티션 수
        private int largeGridSize = 4;      // 큰 데이터용 파티션 수
        private int extraLargeGridSize = 8; // 매우 큰 데이터용 파티션 수
    }

    @Getter
    @Setter
    public static class Pool {
        private int coreSize = 2;
        private int maxSize = 4;
        private int queueCapacity = 25;
        private String threadNamePrefix = "batch-";
    }

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