package com.github.garamflow.streamsettlement.batch.processor;

import com.github.garamflow.streamsettlement.entity.settlement.Settlement;
import com.github.garamflow.streamsettlement.entity.settlement.SettlementRate;
import com.github.garamflow.streamsettlement.entity.settlement.SettlementStatus;
import com.github.garamflow.streamsettlement.entity.settlement.SettlementType;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.exception.SettlementProcessingException;
import com.github.garamflow.streamsettlement.repository.settlement.SettlementRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 통계 데이터를 기반으로 정산 금액을 계산하는 Processor 클래스 (전체 실행 순서 중 4.3단계)
 *
 * <p>통계 데이터를 입력받아 컨텐츠 수익과 광고 수익을 계산하고 정산 데이터를 생성합니다.</p>
 *
 * <p><b>정산 계산 방식:</b></p>
 * <ul>
 *     <li>컨텐츠 정산: 조회수 × 정산 요율</li>
 *     <li>광고 정산: 조회수 × 광고 요율 × 광고 수</li>
 *     <li>총 정산 금액: 컨텐츠 정산 + 광고 정산</li>
 * </ul>
 *
 * <p><b>데이터 흐름:</b></p>
 * <pre>
 * 입력: ContentStatistics (통계 데이터)
 * 출력: Settlement (정산 데이터)
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementProcessor implements ItemProcessor<ContentStatistics, Settlement> {

    private final SettlementRateRepository settlementRateRepository;

    /**
     * 통계 데이터를 정산 데이터로 변환합니다. (실행 순서 1단계)
     *
     * <p><b>처리 과정:</b></p>
     * <ol>
     *     <li>컨텐츠 정산금액 계산
     *         <ul>
     *             <li>조회수에 따른 정산 요율 조회</li>
     *             <li>정산 금액 계산</li>
     *         </ul>
     *     </li>
     *     <li>광고 정산금액 계산
     *         <ul>
     *             <li>영상 길이에 따른 광고 수 계산 (5분당 1개)</li>
     *             <li>광고별 정산 금액 계산</li>
     *         </ul>
     *     </li>
     *     <li>Settlement 객체 생성 및 반환</li>
     * </ol>
     *
     * @param statistics 처리할 통계 데이터
     * @return 생성된 정산 데이터
     * @throws SettlementProcessingException 정산 처리 중 오류 발생 시
     */
    @Override
    public Settlement process(@NonNull ContentStatistics statistics) {
        try {
            LocalDateTime now = LocalDateTime.now();
            Long viewCount = statistics.getViewCount();

            // 컨텐츠 정산금액 계산
            Long contentAmount = calculateAmount(
                    viewCount,
                    SettlementType.CONTENT,
                    now
            );

            // 광고 정산금액 계산 (5분당 1개의 광고)
            int adCount = calculateAdCount(statistics.getContentPost().getDuration());
            Long adAmount = calculateAmount(
                    viewCount,
                    SettlementType.ADVERTISEMENT,
                    now
            ) * adCount;

            return new Settlement.Builder()
                    .contentStatistics(statistics)
                    .settlementDate(statistics.getStatisticsDate())
                    .dailyViews(viewCount)
                    .totalViews(getTotalViews(statistics))
                    .contentAmount(contentAmount)
                    .adAmount(adAmount)
                    .totalAmount(contentAmount + adAmount)
                    .status(SettlementStatus.CALCULATED)
                    .build();

        } catch (Exception e) {
            log.error("Error processing settlement for statistics: {}", statistics.getId(), e);
            throw new SettlementProcessingException("Failed to process settlement", e);
        }
    }

    /**
     * 조회수와 정산 유형에 따른 정산 금액을 계산합니다. (실행 순서 2단계)
     *
     * <p><b>처리 과정:</b></p>
     * <ol>
     *     <li>조회수에 해당하는 정산 요율 조회</li>
     *     <li>정산 금액 계산 (조회수 × 요율)</li>
     *     <li>1원 단위 이하 절사</li>
     * </ol>
     *
     * <p><b>주의사항:</b></p>
     * <ul>
     *     <li>금액 계산 시 정확도를 위해 BigDecimal 사용</li>
     *     <li>적용 가능한 정산 요율이 없으면 예외 발생</li>
     * </ul>
     *
     * @param views 조회수
     * @param type 정산 유형 (CONTENT 또는 ADVERTISEMENT)
     * @param dateTime 정산 기준 시점
     * @return 계산된 정산 금액
     * @throws IllegalStateException 적용 가능한 정산 요율이 없는 경우
     */
    private Long calculateAmount(Long views, SettlementType type, LocalDateTime dateTime) {
        // 해당 시점에 적용 가능한 정산 비율 조회
        SettlementRate rate = settlementRateRepository.findApplicableRate(type, views, dateTime)
                .orElseThrow(() -> new IllegalStateException(
                        "No applicable settlement rate found for type: " + type +
                                ", views: " + views));

        // BigDecimal 을 사용하여 정확한 계산
        BigDecimal amount = new BigDecimal(views)
                .multiply(rate.getRate())
                .setScale(0, RoundingMode.DOWN); // 1원 단위 이하 절사

        return amount.longValue();
    }

    /**
     * 영상 길이에 따른 광고 수를 계산합니다. (실행 순서 3단계)
     *
     * <p><b>계산 방식:</b></p>
     * <ul>
     *     <li>5분당 1개의 광고 배정</li>
     *     <li>예: 12분 영상 → 3개의 광고 (올림 처리)</li>
     * </ul>
     *
     * <p><b>특수 케이스:</b></p>
     * <ul>
     *     <li>영상 길이가 null 이거나 0 이하인 경우 → 0개 반환</li>
     *     <li>4분 59초 → 1개</li>
     *     <li>5분 1초 → 2개</li>
     * </ul>
     *
     * @param videoLength 영상 길이(초 단위)
     * @return 계산된 광고 수
     */
    private int calculateAdCount(Integer videoLength) {
        if (videoLength == null || videoLength <= 0) {
            return 0;
        }
        return (int) Math.ceil(videoLength / (5.0 * 60));  // 5분당 1개
    }

    /**
     * 통계 데이터에서 누적 조회수를 조회합니다.
     *
     * @param statistics 통계 데이터
     * @return 누적 조회수
     */
    private Long getTotalViews(ContentStatistics statistics) {
        return statistics.getAccumulatedViews();
    }
}
