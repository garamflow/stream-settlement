package com.github.garamflow.streamsettlement.batch.processor;

import com.github.garamflow.streamsettlement.entity.member.Member;
import com.github.garamflow.streamsettlement.entity.member.Role;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import com.github.garamflow.streamsettlement.entity.stream.Log.DailyMemberViewLog;
import com.github.garamflow.streamsettlement.entity.stream.Log.StreamingStatus;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.MetaDataInstanceFactory;
import org.springframework.batch.test.StepScopeTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@SpringBatchTest
@ExtendWith(SpringExtension.class)
class DailyLogProcessorTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private DailyLogProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new DailyLogProcessor();
    }

    @Test
    void 완료된_시청로그를_통계로_변환한다() throws Exception {
        // given
        LocalDate today = LocalDate.now();
        Member member = createMember();
        ContentPost contentPost = createContentPost(member);
        DailyMemberViewLog log = createCompletedLog(member, contentPost, today);

        // when
        StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
        StepScopeTestUtils.doInStepScope(stepExecution, () -> {
            List<ContentStatistics> result = processor.process(log);

            // then
            assertThat(result)
                    .hasSize(4) // DAILY, WEEKLY, MONTHLY, YEARLY
                    .allSatisfy(stats -> {
                        assertThat(stats.getContentPost()).isEqualTo(contentPost);
                        assertThat(stats.getViewCount()).isEqualTo(1L);
                        assertThat(stats.getWatchTime()).isEqualTo(100L);
                    });

            // 각 통계 기간별 날짜와 기간 검증
            assertThat(result).extracting("period", "statisticsDate")
                    .containsExactly(
                            tuple(StatisticsPeriod.DAILY, today),
                            tuple(StatisticsPeriod.WEEKLY, today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))),
                            tuple(StatisticsPeriod.MONTHLY, today.withDayOfMonth(1)),
                            tuple(StatisticsPeriod.YEARLY, today.withDayOfYear(1))
                    );
            return null;
        });
    }

    @Test
    void 미완료_시청로그는_처리하지_않는다() throws Exception {
        // given
        Member member = createMember();
        ContentPost contentPost = createContentPost(member);
        DailyMemberViewLog log = createInProgressLog(member, contentPost, LocalDate.now());

        // when
        StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
        StepScopeTestUtils.doInStepScope(stepExecution, () -> {
            List<ContentStatistics> result = processor.process(log);

            // then
            assertThat(result).isNull();
            return null;
        });
    }

    @Test
    void Member가_null이면_예외를_던진다() throws Exception {
        // given
        Member member = createMember();
        ContentPost contentPost = createContentPost(member);
        DailyMemberViewLog log = createCompletedLog(member, contentPost, LocalDate.now());
        ReflectionTestUtils.setField(log, "member", null);

        // when & then
        StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
        StepScopeTestUtils.doInStepScope(stepExecution, () -> {
            assertThatThrownBy(() -> processor.process(log))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("member");
            return null;
        });
    }

    @Test
    void ContentPost가_null이면_예외를_던진다() throws Exception {
        // given
        Member member = createMember();
        ContentPost contentPost = createContentPost(member);
        DailyMemberViewLog log = createCompletedLog(member, contentPost, LocalDate.now());
        ReflectionTestUtils.setField(log, "contentPost", null);

        // when & then
        StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
        StepScopeTestUtils.doInStepScope(stepExecution, () -> {
            assertThatThrownBy(() -> processor.process(log))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("contentPost");
            return null;
        });
    }

    private Member createMember() {
        return new Member.Builder()
                .email("test@test.com")
                .role(Role.MEMBER)
                .build();
    }

    private ContentPost createContentPost(Member member) {
        return ContentPost.builder()
                .member(member)
                .title("테스트 영상")
                .url("http://test.com/video")
                .build();
    }

    private DailyMemberViewLog createCompletedLog(Member member, ContentPost contentPost, LocalDate date) {
        return DailyMemberViewLog.builder()
                .member(member)
                .contentPost(contentPost)
                .lastViewedPosition(100)
                .lastAdViewCount(2)
                .logDate(date)
                .status(StreamingStatus.COMPLETED)
                .build();
    }

    private DailyMemberViewLog createInProgressLog(Member member, ContentPost contentPost, LocalDate date) {
        return DailyMemberViewLog.builder()
                .member(member)
                .contentPost(contentPost)
                .lastViewedPosition(50)
                .lastAdViewCount(1)
                .logDate(date)
                .status(StreamingStatus.IN_PROGRESS)
                .build();
    }
}