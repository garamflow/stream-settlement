package com.github.garamflow.streamsettlement.batch.config;

import com.github.garamflow.streamsettlement.entity.member.Member;
import com.github.garamflow.streamsettlement.entity.member.Role;
import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import com.github.garamflow.streamsettlement.entity.stream.Log.DailyMemberViewLog;
import com.github.garamflow.streamsettlement.entity.stream.Log.StreamingStatus;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import com.github.garamflow.streamsettlement.repository.advertisement.AdvertisementContentPostRepository;
import com.github.garamflow.streamsettlement.repository.statistics.ContentStatisticsRepository;
import com.github.garamflow.streamsettlement.repository.stream.ContentPostRepository;
import com.github.garamflow.streamsettlement.repository.stream.DailyMemberViewLogRepository;
import com.github.garamflow.streamsettlement.repository.user.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@SpringBatchTest
class DailyLogAggregationJobConfigTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private DailyMemberViewLogRepository viewLogRepository;

    @Autowired
    private ContentStatisticsRepository contentStatisticsRepository;

    @Autowired
    private ContentPostRepository contentPostRepository;

    @Autowired
    private AdvertisementContentPostRepository advertisementContentPostRepository;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @BeforeEach
    void setUp() {
        jobRepositoryTestUtils.removeJobExecutions();

        // 1. 통계 데이터 삭제
        contentStatisticsRepository.deleteAll();

        // 2. 시청 로그 삭제
        viewLogRepository.deleteAll();

        // 3. 광고-컨텐츠 연관 데이터 삭제 (새로 추가)
        advertisementContentPostRepository.deleteAll();

        // 4. 컨텐츠 삭제
        contentPostRepository.deleteAll();

        // 5. 회원 삭제
        memberRepository.deleteAll();
    }

    @Test
    void 일간_통계_집계_Job이_정상적으로_실행된다() throws Exception {
        // given
        LocalDate today = LocalDate.now();
        Member member = createMember();
        ContentPost contentPost = createContentPost(member);
        createCompletedViewLogs(member, contentPost, today);

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("targetDate", today.toString())
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        // when
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // then
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        List<ContentStatistics> statistics = contentStatisticsRepository.findAll();
        assertThat(statistics).hasSize(1);

        ContentStatistics stat = statistics.get(0);
        assertThat(stat.getPeriod()).isEqualTo(StatisticsPeriod.DAILY);
        assertThat(stat.getViewCount()).isEqualTo(3L);
        assertThat(stat.getWatchTime()).isGreaterThan(0L);
    }

    private Member createMember() {
        String uniqueEmail = "test" + System.currentTimeMillis() + "@test.com";
        Member member = new Member.Builder()
                .email(uniqueEmail)
                .role(Role.MEMBER)
                .build();

        return memberRepository.save(member); // 영속 상태로 저장
    }

    private ContentPost createContentPost(Member member) {
        ContentPost contentPost = ContentPost.builder()
                .member(member)
                .title("테스트 영상")
                .url("http://test.com/video")
                .build();

        return contentPostRepository.save(contentPost);
    }

    private void createCompletedViewLogs(Member member, ContentPost contentPost, LocalDate date) {
        // ContentPost 다시 저장하지 않음

        // 완료된 로그 3개 생성
        for (int i = 0; i < 3; i++) {
            DailyMemberViewLog log = DailyMemberViewLog.builder()
                    .member(member)
                    .contentPost(contentPost)
                    .lastViewedPosition(100 * (i + 1))
                    .lastAdViewCount(i)
                    .logDate(date)  // 파라미터로 받은 날짜 사용
                    .status(StreamingStatus.COMPLETED)
                    .build();
            viewLogRepository.save(log);
        }

        // 진행 중인 로그 1개 생성
        DailyMemberViewLog inProgressLog = DailyMemberViewLog.builder()
                .member(member)
                .contentPost(contentPost)  // 이미 저장된 contentPost 사용
                .lastViewedPosition(50)
                .lastAdViewCount(0)
                .logDate(LocalDate.now())
                .status(StreamingStatus.IN_PROGRESS)
                .build();
        viewLogRepository.save(inProgressLog);
    }
}