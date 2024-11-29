package com.github.garamflow.streamsettlement.batch.reader;

import com.github.garamflow.streamsettlement.entity.member.Member;
import com.github.garamflow.streamsettlement.entity.member.Role;
import com.github.garamflow.streamsettlement.entity.stream.Log.DailyMemberViewLog;
import com.github.garamflow.streamsettlement.entity.stream.Log.StreamingStatus;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import com.github.garamflow.streamsettlement.repository.stream.ContentPostRepository;
import com.github.garamflow.streamsettlement.repository.stream.DailyMemberViewLogRepository;
import com.github.garamflow.streamsettlement.repository.user.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ExtendWith(SpringExtension.class)
class DailyLogReaderTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DailyLogReader dailyLogReader;

    @Autowired
    private DailyMemberViewLogRepository viewLogRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ContentPostRepository contentPostRepository;

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job dailyLogAggregationJob;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM daily_member_view_log");
        jdbcTemplate.execute("DELETE FROM content_statistics");
        jdbcTemplate.execute("DELETE FROM advertisement_content_post");
        jdbcTemplate.execute("DELETE FROM content_post");
        jdbcTemplate.execute("DELETE FROM member");
    }

    @Test
    void reader는_특정_날짜의_로그를_모두_읽어온다() throws Exception {
        // given
        LocalDate targetDate = LocalDate.of(2024, 3, 20);
        Member member = createMember();
        ContentPost contentPost = createContentPost(member);
        createViewLogs(member, contentPost, targetDate);

        Map<String, Object> minMax = jdbcTemplate.queryForMap("""
                SELECT MIN(content_post_id) as min_id, 
                       MAX(content_post_id) as max_id 
                FROM content_post
                """);

        Long minId = ((Number) minMax.get("min_id")).longValue();
        Long maxId = ((Number) minMax.get("max_id")).longValue();

        JdbcPagingItemReader<DailyMemberViewLog> reader = dailyLogReader.reader(
                targetDate.toString(),
                minId,
                maxId
        );
        reader.afterPropertiesSet();

        // when
        List<DailyMemberViewLog> results = new ArrayList<>();
        DailyMemberViewLog item;
        while ((item = reader.read()) != null) {
            results.add(item);
        }

        // then
        assertThat(results).hasSize(3);
        assertThat(results).allMatch(log -> {
            assertThat(log.getLogDate()).isEqualTo(targetDate);
            assertThat(log.getStatus()).isEqualTo(StreamingStatus.COMPLETED);
            assertThat(log.getMember()).isEqualTo(member);
            assertThat(log.getContentPost()).isEqualTo(contentPost);
            return true;
        });
    }

    @Test
    void reader가_minId_또는_maxId_누락시_예외를_던진다() {
        // given
        LocalDate targetDate = LocalDate.of(2024, 3, 20);

        // when & then
        assertThatThrownBy(() -> dailyLogReader.reader(targetDate.toString(), null, 100L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("StepExecutionContext missing 'minId' or 'maxId'");

        assertThatThrownBy(() -> dailyLogReader.reader(targetDate.toString(), 1L, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("StepExecutionContext missing 'minId' or 'maxId'");
    }


    @Test
    void testJobExecution() throws Exception {
        // given
        LocalDate targetDate = LocalDate.of(2024, 3, 20);
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("targetDate", targetDate.toString())
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        // when
        JobExecution jobExecution = jobLauncher.run(dailyLogAggregationJob, jobParameters);

        // then
        assertEquals(BatchStatus.COMPLETED, jobExecution.getStatus());
    }

    private Member createMember() {
        String uniqueEmail = "test" + System.currentTimeMillis() + "@test.com";
        Member member = new Member.Builder()
                .email(uniqueEmail)
                .role(Role.MEMBER)
                .build();
        return memberRepository.save(member);
    }

    private ContentPost createContentPost(Member member) {
        ContentPost contentPost = ContentPost.builder()
                .member(member)
                .title("테스트 영상")
                .url("http://test.com/video")
                .build();
        return contentPostRepository.save(contentPost);
    }

    private void createViewLogs(Member member, ContentPost contentPost, LocalDate date) {
        for (int i = 0; i < 3; i++) {
            viewLogRepository.save(DailyMemberViewLog.builder()
                    .member(member)
                    .contentPost(contentPost)
                    .lastViewedPosition(100 * (i + 1))
                    .lastAdViewCount(i)
                    .logDate(date)
                    .status(StreamingStatus.COMPLETED)
                    .build());
        }
    }
}