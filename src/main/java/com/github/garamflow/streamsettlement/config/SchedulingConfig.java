package com.github.garamflow.streamsettlement.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 스케줄링 설정
 * - 스케줄링 기능 활성화
 * - 스케줄링 작업을 위한 스레드 풀 구성
 * - 에러 처리 로직 포함
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    private static final Logger log = LoggerFactory.getLogger(SchedulingConfig.class);

    /**
     * 스케줄링 작업을 위한 ThreadPool 설정
     * - 풀 사이즈: 5
     * - 에러 핸들링 포함
     * - 스레드 이름 prefix 지정
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("scheduled-task-");
        scheduler.setErrorHandler(throwable ->
                log.error("Scheduled task error", throwable));
        scheduler.initialize();
        return scheduler;
    }
} 