package com.github.garamflow.streamsettlement.batch.config;

import com.github.garamflow.streamsettlement.batch.listener.StatisticsStepListener;
import com.github.garamflow.streamsettlement.batch.processor.CompositeStatisticsProcessor;
import com.github.garamflow.streamsettlement.batch.reader.DailyLogReader;
import com.github.garamflow.streamsettlement.batch.writer.StatisticsWriter;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.stream.Log.DailyMemberViewLog;
import com.github.garamflow.streamsettlement.exception.StatisticsBatchSkipException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.LockAcquisitionException;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 통계 데이터 집계를 위한 파티셔닝 설정 클래스
 *
 * <p>일별 시청 로그 데이터를 날짜 단위로 파티셔닝하여 병렬 처리합니다.</p>
 *
 * <p><b>주요 컴포넌트:</b></p>
 * <ul>
 *     <li>Master Step (partitionStep): 파티션 관리 및 Worker Step 실행 관리</li>
 *     <li>Worker Step: 각 날짜별 데이터 처리</li>
 *     <li>TaskExecutor: Worker Step 병렬 실행을 위한 스레드 풀</li>
 * </ul>
 *
 * <p><b>설정 속성:</b></p>
 * <ul>
 *     <li>spring.batch.partition.pool-size: 병렬 처리 스레드 수</li>
 *     <li>spring.batch.chunk-size: 청크 단위 크기</li>
 * </ul>
 *
 * <p><b>오류 처리:</b></p>
 * <ul>
 *     <li>재시도: 동시성 관련 예외 3회까지 재시도</li>
 *     <li>건너뛰기: StatisticsBatchSkipException 10회까지 허용</li>
 * </ul>
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(BatchProperties.class)
@RequiredArgsConstructor
public class StatisticsPartitionConfig {


    private final BatchProperties batchProperties;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DailyLogReader dailyLogReader;
    private final CompositeStatisticsProcessor compositeStatisticsProcessor;
    private final StatisticsWriter statisticsWriter;
    private final StatisticsStepListener statisticsStepListener;


    /**
     * 날짜별 파티션을 생성하는 Partitioner 를 구성합니다.
     *
     * <p><b>파티션 생성 방식:</b></p>
     * <ol>
     *     <li>종료일: 전일(현재 날짜 - 1일)</li>
     *     <li>시작일: 종료일 - (gridSize - 1)일</li>
     *     <li>각 날짜별로 별도의 ExecutionContext 생성</li>
     * </ol>
     *
     * <p>각 ExecutionContext 에는 해당 파티션이 처리할 날짜가 'date' 키로 저장됩니다.</p>
     *
     * @return 날짜별 파티셔닝을 수행하는 Partitioner
     */
    @Bean
    public Partitioner statisticsPartitioner() {
        return new Partitioner() {
            @Override
            @NonNull
            public Map<String, ExecutionContext> partition(int gridSize) {
                LocalDate endDate = LocalDate.now().minusDays(1);
                LocalDate startDate = endDate.minusDays(gridSize - 1);

                Map<String, ExecutionContext> result = new HashMap<>();
                int partitionIndex = 0;

                LocalDate currentDate = startDate;
                while (!currentDate.isAfter(endDate)) {
                    ExecutionContext context = new ExecutionContext();
                    context.putString("date", currentDate.toString());
                    result.put("partition" + partitionIndex, context);

                    currentDate = currentDate.plusDays(1);
                    partitionIndex++;
                }

                log.info("Partition created: {} - {}, total partitions: {}",
                        startDate, endDate, result.size());

                return result;
            }
        };
    }

    /**
     * Worker Step 병렬 실행을 위한 ThreadPool 을 구성합니다.
     *
     * <p><b>스레드 풀 설정:</b></p>
     * <ul>
     *     <li>코어 스레드 수: 설정된 pool-size</li>
     *     <li>최대 스레드 수: 설정된 pool-size</li>
     *     <li>스레드 이름 접두어: statistics-partition-</li>
     *     <li>종료 대기: 최대 30초</li>
     * </ul>
     *
     * @return 구성된 ThreadPoolTaskExecutor
     */
    @Bean
    public TaskExecutor statisticsTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(batchProperties.getPartition().getPoolSize());
        executor.setMaxPoolSize(batchProperties.getPartition().getPoolSize());
        executor.setThreadNamePrefix("statistics-partition-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * 파티셔닝을 관리하는 Master Step 을 구성합니다.
     *
     * <p><b>Master Step 구성:</b></p>
     * <ol>
     *     <li>Partitioner 를 통해 날짜별 파티션 생성</li>
     *     <li>각 파티션에 대해 Worker Step 실행</li>
     *     <li>TaskExecutor 를 통한 병렬 처리</li>
     * </ol>
     *
     * @return 구성된 파티셔닝 Master Step
     */
    @Bean
    public Step partitionStep() {
        return new StepBuilder("statisticsPartitionStep", jobRepository)
                .partitioner("statisticsStep", statisticsPartitioner())
                .step(workerStep())
                .gridSize(batchProperties.getPartition().getPoolSize())
                .taskExecutor(statisticsTaskExecutor())
                .build();
    }

    /**
     * 개별 날짜 데이터를 처리하는 Worker Step 을 구성합니다.
     *
     * <p><b>처리 과정:</b></p>
     * <ol>
     *     <li>DailyLogReader: 해당 날짜의 로그 데이터 읽기</li>
     *     <li>CompositeStatisticsProcessor: 통계 데이터 생성</li>
     *     <li>StatisticsWriter: 생성된 통계 저장</li>
     * </ol>
     *
     * <p><b>오류 처리:</b></p>
     * <ul>
     *     <li>재시도 가능 예외:
     *         <ul>
     *             <li>ConcurrencyFailureException</li>
     *             <li>LockAcquisitionException</li>
     *             <li>CannotAcquireLockException</li>
     *         </ul>
     *     </li>
     *     <li>건너뛰기 가능 예외: StatisticsBatchSkipException</li>
     *     <li>롤백 제외 예외: ResourceAccessException</li>
     * </ul>
     *
     * @return 구성된 Worker Step
     */
    @Bean
    public Step workerStep() {
        return new StepBuilder("statisticsStep", jobRepository)
                .<DailyMemberViewLog, List<ContentStatistics>>chunk(batchProperties.getChunkSize(), transactionManager)
                .reader(dailyLogReader)
                .processor(compositeStatisticsProcessor)
                .writer(statisticsWriter)
                .faultTolerant()
                .retryLimit(3)
                .retry(ConcurrencyFailureException.class)
                .retry(LockAcquisitionException.class)
                .retry(CannotAcquireLockException.class)
                .skipLimit(10)
                .skip(StatisticsBatchSkipException.class)
                .noRollback(ResourceAccessException.class)
                .listener(statisticsStepListener)
                .build();
    }
}
