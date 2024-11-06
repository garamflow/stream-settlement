package com.github.garamflow.streamsettlement.batch.processor;

import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import com.github.garamflow.streamsettlement.entity.stream.Log.DailyMemberViewLog;
import com.github.garamflow.streamsettlement.exception.StatisticsProcessingException;
import com.github.garamflow.streamsettlement.repository.statistics.StatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static java.time.temporal.TemporalAdjusters.previousOrSame;

/**
 * 시청 로그를 여러 기간별 통계로 변환하는 Processor 클래스 (전체 실행 순서 중 3.3단계)
 *
 * <p>하나의 시청 로그를 일별/주별/월별/연별 통계로 변환합니다.</p>
 *
 * <p><b>실행 순서:</b></p>
 * <ol>
 *     <li>process() 메서드 호출
 *         <ul>
 *             <li>로그 데이터 수신</li>
 *             <li>각 기간별 통계 생성</li>
 *         </ul>
 *     </li>
 *     <li>createStatistics() - 각 기간별 반복
 *         <ul>
 *             <li>통계 기준일자 계산</li>
 *             <li>누적 조회수 계산</li>
 *             <li>통계 데이터 생성</li>
 *         </ul>
 *     </li>
 * </ol>
 *
 * <p><b>통계 기간:</b></p>
 * <ul>
 *     <li>DAILY: 해당 일자</li>
 *     <li>WEEKLY: 해당 주의 월요일</li>
 *     <li>MONTHLY: 해당 월의 1일</li>
 *     <li>YEARLY: 해당 연도의 1월 1일</li>
 * </ul>
 *
 * <p><b>데이터 흐름:</b></p>
 * <pre>
 * 입력: DailyMemberViewLog
 * 출력: List<ContentStatistics> (기간별 4개의 통계)
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompositeStatisticsProcessor implements ItemProcessor<DailyMemberViewLog, List<ContentStatistics>> {

    private final StatisticsRepository statisticsRepository;

    /**
     * 단일 시청 로그를 여러 기간의 통계 데이터로 변환합니다. (실행 순서 1단계)
     *
     * <p>Spring Batch 의 청크 처리 과정에서 reader 가 읽어온 각 로그 데이터에 대해 호출됩니다.</p>
     *
     * <p><b>처리 과정:</b></p>
     * <ol>
     *     <li>입력된 로그의 날짜 추출</li>
     *     <li>모든 통계 기간(DAILY, WEEKLY, MONTHLY, YEARLY)에 대해:
     *         <ul>
     *             <li>해당 기간의 통계 데이터 생성</li>
     *             <li>생성된 통계 데이터를 리스트에 추가</li>
     *         </ul>
     *     </li>
     * </ol>
     *
     * @param dailyMemberViewLog 처리할 시청 로그 데이터 (null 이 아님)
     * @return 생성된 통계 데이터 리스트 (기간별 4개 항목)
     * @throws StatisticsProcessingException 통계 처리 중 오류 발생 시
     */
    @Override
    public List<ContentStatistics> process(@NonNull DailyMemberViewLog dailyMemberViewLog) {
        try {
            LocalDate logDate = dailyMemberViewLog.getLogDate();
            List<ContentStatistics> statistics = new ArrayList<>();

            // 모든 기간의 통계를 한번에 생성
            for (StatisticsPeriod period : StatisticsPeriod.values()) {
                ContentStatistics stat = createStatistics(dailyMemberViewLog, logDate, period);
                statistics.add(stat);
                log.debug("Created {} statistics for content {}",
                        period, dailyMemberViewLog.getContentPost().getId());
            }

            return statistics;

        } catch (Exception e) {
            log.error("Error processing statistics for log: {}", dailyMemberViewLog.getId(), e);
            throw new StatisticsProcessingException("Failed to process statistics", e);
        }
    }

    /**
     * 특정 기간에 대한 통계 데이터를 생성합니다. (실행 순서 2단계)
     *
     * <p><b>처리 과정:</b></p>
     * <ol>
     *     <li>해당 기간의 통계 기준일자 계산</li>
     *     <li>이전 누적 조회수 조회
     *         <ul>
     *             <li>기존 통계가 있는 경우: 해당 값 사용</li>
     *             <li>기존 통계가 없는 경우: 0으로 시작</li>
     *         </ul>
     *     </li>
     *     <li>새로운 누적 조회수 계산 (이전 누적값 + 1)</li>
     *     <li>통계 데이터 생성 및 반환</li>
     * </ol>
     *
     * @param log 시청 로그 데이터
     * @param logDate 로그 발생 일자
     * @param period 통계 기간 (DAILY, WEEKLY, MONTHLY, YEARLY)
     * @return 생성된 통계 데이터
     */
    private ContentStatistics createStatistics(
            DailyMemberViewLog log,
            LocalDate logDate,
            StatisticsPeriod period) {

        LocalDate statisticsDate = calculateStatisticsDate(logDate, period);

        // 이전 통계 데이터에서 누적 조회수 가져오기
        Long previousAccumulatedViews = statisticsRepository
                .findLastAccumulatedViews(
                        log.getContentPost().getId(),
                        statisticsDate,
                        period
                ).orElse(0L);

        Long newAccumulatedViews = previousAccumulatedViews + 1L;

        return new ContentStatistics.Builder()
                .contentPost(log.getContentPost())
                .statisticsDate(statisticsDate)
                .period(period)
                .viewCount(1L)
                .watchTime((long) log.getLastViewedPosition())
                .accumulatedViews(newAccumulatedViews)  // 누적 조회수 설정
                .build();
    }

    /**
     * 주어진 로그 일자와 통계 기간에 따른 통계 기준일자를 계산합니다. (실행 순서 3단계)
     *
     * <p><b>기간별 계산 방식:</b></p>
     * <ul>
     *     <li>DAILY: 로그 일자 그대로 사용</li>
     *     <li>WEEKLY: 해당 주의 월요일로 설정</li>
     *     <li>MONTHLY: 해당 월의 1일로 설정</li>
     *     <li>YEARLY: 해당 연도의 1월 1일로 설정</li>
     * </ul>
     *
     * <p><b>예시:</b></p>
     * <pre>
     * 로그일자: 2024-04-15
     * - DAILY → 2024-04-15
     * - WEEKLY → 2024-04-15 (월요일이므로 그대로)
     * - MONTHLY → 2024-04-01
     * - YEARLY → 2024-01-01
     * </pre>
     *
     * @param logDate 로그 발생 일자
     * @param period 통계 기간
     * @return 계산된 통계 기준일자
     */
    private LocalDate calculateStatisticsDate(LocalDate logDate, StatisticsPeriod period) {
        return switch (period) {
            case DAILY -> logDate;
            case WEEKLY -> logDate.with(previousOrSame(DayOfWeek.MONDAY));
            case MONTHLY -> logDate.withDayOfMonth(1);
            case YEARLY -> logDate.withDayOfYear(1);
        };
    }
}
