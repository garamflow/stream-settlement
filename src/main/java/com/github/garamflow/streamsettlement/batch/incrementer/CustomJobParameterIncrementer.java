package com.github.garamflow.streamsettlement.batch.incrementer;

import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersIncrementer;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 배치 작업의 JobParameter를 자동으로 생성하는 인크리멘터
 * - 매일 실행되는 배치 작업의 targetDate 파라미터를 자동으로 설정
 * - 전일 날짜를 targetDate로 설정하여 전날의 데이터를 처리
 */
@Component
public class CustomJobParameterIncrementer implements JobParametersIncrementer {

    /**
     * 다음 실행할 배치의 JobParameters 생성
     * - 현재 날짜에서 1일을 뺀 날짜(전일)를 targetDate로 설정
     *
     * @param parameters 현재 JobParameters (사용하지 않음)
     * @return 새로운 JobParameters
     */
    @Override
    @NonNull
    public JobParameters getNext(JobParameters parameters) {
        LocalDate targetDate = LocalDate.now().minusDays(1L);
        return new JobParametersBuilder()
                .addString("targetDate", targetDate.toString())
                .toJobParameters();
    }
}

