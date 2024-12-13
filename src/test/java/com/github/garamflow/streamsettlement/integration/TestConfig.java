package com.github.garamflow.streamsettlement.integration;

import com.github.garamflow.streamsettlement.scheduler.ViewCountSyncScheduler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

@TestConfiguration
public class TestConfig {
    @Bean
    @Primary
    public TaskScheduler taskScheduler() {
        return new NoOpTaskScheduler();
    }

    @Bean
    @Primary
    public ViewCountSyncScheduler viewCountSyncScheduler() {
        return new ViewCountSyncScheduler(null, null) {
            @Override
            public void syncContentViewCountsToDatabase() {
                // 아무 동작도 하지 않음
            }
        };
    }

    static class NoOpTaskScheduler implements TaskScheduler {
        @Override
        public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
            return null;
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
            return null;
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, Duration period) {
            return null;
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
            return null;
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, Duration delay) {
            return null;
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
            return null;
        }
    }
}