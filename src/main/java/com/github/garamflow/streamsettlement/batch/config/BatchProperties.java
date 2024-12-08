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

    @Getter
    @Setter
    public static class Partition {
        private int smallDataSize = 10000;    // 10000개 미만
        private int mediumDataSize = 100000;  // 100000개 미만
        private int largeDataSize = 1000000;  // 1000000개 미만

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
} 