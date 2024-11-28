package com.github.garamflow.streamsettlement;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableBatchProcessing
public class StreamSettlementApplication {

    public static void main(String[] args) {
        SpringApplication.run(StreamSettlementApplication.class, args);
    }

}
