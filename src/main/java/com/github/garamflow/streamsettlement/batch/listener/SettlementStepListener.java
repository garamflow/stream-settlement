package com.github.garamflow.streamsettlement.batch.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 정산 처리 Step 의 실행 전후를 관리하는 Listener 클래스 (전체 실행 순서 중 4.1/4.5단계)
 *
 * <p>정산 처리 시작 전 필수 조건을 검증하고, 처리 완료 후 실행 결과를 기록합니다.</p>
 *
 * <p><b>주요 기능:</b></p>
 * <ul>
 *     <li>통계 처리 완료 여부 확인</li>
 *     <li>정산 기준일자 검증</li>
 *     <li>처리 시간 측정</li>
 *     <li>처리 결과 로깅</li>
 * </ul>
 *
 * <p><b>실행 조건:</b></p>
 * <ul>
 *     <li>이전 통계 스텝이 성공적으로 완료되어야 함</li>
 *     <li>정산 처리할 날짜 정보가 있어야 함</li>
 *     <li>처리할 통계 데이터가 존재해야 함</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementStepListener implements StepExecutionListener {

    /**
     * 정산 처리 시작 전 검증을 수행합니다. (실행 순서 4.1단계)
     *
     * <p><b>검증 항목:</b></p>
     * <ol>
     *     <li>통계 처리 완료 여부
     *         <ul>
     *             <li>statisticsProcessed 플래그 확인</li>
     *             <li>미완료 시 예외 발생</li>
     *         </ul>
     *     </li>
     *     <li>정산 처리 날짜
     *         <ul>
     *             <li>settlementDate 존재 여부 확인</li>
     *             <li>누락 시 예외 발생</li>
     *         </ul>
     *     </li>
     *     <li>통계 데이터 수
     *         <ul>
     *             <li>totalStatisticsCount 확인</li>
     *             <li>0인 경우 경고 로그 기록</li>
     *         </ul>
     *     </li>
     * </ol>
     *
     * @param stepExecution 현재 Step 의 실행 정보
     * @throws IllegalStateException 필수 조건이 충족되지 않은 경우
     */
    @Override
    public void beforeStep(@NonNull StepExecution stepExecution) {
        ExecutionContext jobContext = stepExecution.getJobExecution().getExecutionContext();

        // 통계 처리 완료 여부 확인
        if (!jobContext.containsKey("statisticsProcessed") ||
                !Boolean.parseBoolean(jobContext.getString("statisticsProcessed", "false"))) {
            throw new IllegalStateException("Statistics step must be completed before settlement");
        }

        // 정산 처리할 날짜 확인
        LocalDate settlementDate = (LocalDate) jobContext.get("settlementDate");
        if (settlementDate == null) {
            throw new IllegalStateException("Settlement date not found in job context");
        }

        // 처리할 통계 데이터 수 확인
        long totalStatistics = jobContext.getLong("totalStatisticsCount", 0);
        if (totalStatistics == 0) {
            log.warn("No statistics found for settlement processing");
        }

        stepExecution.getExecutionContext().put("startTime", System.currentTimeMillis());
        log.info("Starting settlement processing for date: {}", settlementDate);
    }

    /**
     * 정산 처리 완료 후 결과를 기록합니다. (실행 순서 4.5단계)
     *
     * <p><b>처리 내용:</b></p>
     * <ol>
     *     <li>처리 시간 계산
     *         <ul>
     *             <li>시작 시간과 현재 시간의 차이 계산</li>
     *             <li>밀리초 단위로 기록</li>
     *         </ul>
     *     </li>
     *     <li>처리 결과 로깅
     *         <ul>
     *             <li>처리된 레코드 수</li>
     *             <li>총 소요 시간</li>
     *         </ul>
     *     </li>
     * </ol>
     *
     * @param stepExecution 현재 Step 의 실행 정보
     * @return ExitStatus.COMPLETED 정상 완료 시 / ExitStatus.FAILED 오류 발생 시
     */
    @Override
    @NonNull
    public ExitStatus afterStep(@NonNull StepExecution stepExecution) {
        try {
            ExecutionContext stepContext = stepExecution.getExecutionContext();
            long startTime = stepContext.getLong("startTime", 0L);
            long duration = System.currentTimeMillis() - startTime;

            log.info("Completed settlement processing. Processed {} records in {}ms",
                    stepExecution.getWriteCount(), duration);

            return ExitStatus.COMPLETED;

        } catch (Exception e) {
            log.error("Error in settlement step listener", e);
            return ExitStatus.FAILED.addExitDescription(e.getMessage());
        }
    }
}