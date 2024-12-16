package com.github.garamflow.streamsettlement.batch.incrementer;

import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersIncrementer;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class CustomJobParameterIncrementer implements JobParametersIncrementer {

    @Override
    @NonNull
    public JobParameters getNext(JobParameters parameters) {
        LocalDate targetDate = LocalDate.now().minusDays(1L);
        return new JobParametersBuilder()
                .addString("targetDate", targetDate.toString())
                .toJobParameters();
    }
}

