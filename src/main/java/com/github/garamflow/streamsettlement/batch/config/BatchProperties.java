package com.github.garamflow.streamsettlement.batch.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.batch")
@Getter
@Setter
public class BatchProperties {
    private Partition partition = new Partition();
    private int chunkSize = 100;

    @Getter
    @Setter
    public static class Partition {
        private int poolSize = 4;
    }
}
