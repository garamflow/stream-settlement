package com.github.garamflow.streamsettlement.batch.reader;

import com.github.garamflow.streamsettlement.entity.member.Member;
import com.github.garamflow.streamsettlement.entity.member.Role;
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
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@SpringBatchTest
class DailyLogReaderTest {

    @Autowired
    private DailyLogReader dailyLogReader;

    @Autowired
    private DailyMemberViewLogRepository viewLogRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ContentPostRepository contentPostRepository;

    @Autowired
    private ContentStatisticsRepository contentStatisticsRepository;

    @Autowired
    private AdvertisementContentPostRepository advertisementContentPostRepository;

    @BeforeEach
    void setUp() {
        contentStatisticsRepository.deleteAll();
        advertisementContentPostRepository.deleteAll();
        viewLogRepository.deleteAll();
        contentPostRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    void 특정_날짜의_로그를_정상적으로_읽어온다() throws Exception {
        // given
        LocalDate targetDate = LocalDate.now();
        Member member = createMember();
        ContentPost contentPost = createContentPost(member);
        List<DailyMemberViewLog> createdLogs = createViewLogs(member, contentPost, targetDate, 3);

        // ID 범위 설정
        long minId = createdLogs.get(0).getId();
        long maxId = createdLogs.get(createdLogs.size() - 1).getId();

        // when
        JdbcPagingItemReader<DailyMemberViewLog> reader = dailyLogReader.reader(
                targetDate.toString(),
                minId,
                maxId
        );
        reader.afterPropertiesSet();

        // then
        List<DailyMemberViewLog> logs = new ArrayList<>();
        DailyMemberViewLog log;
        while ((log = reader.read()) != null) {
            logs.add(log);
        }

        assertThat(logs)
                .hasSize(3)
                .allSatisfy(viewLog -> {
                    assertThat(viewLog.getMember()).isEqualTo(member);
                    assertThat(viewLog.getContentPost()).isEqualTo(contentPost);
                    assertThat(viewLog.getLogDate()).isEqualTo(targetDate);
                    assertThat(viewLog.getStatus()).isEqualTo(StreamingStatus.COMPLETED);
                });
    }

    @Test
    void ID_범위를_벗어난_로그는_읽지_않는다() throws Exception {
        // given
        LocalDate targetDate = LocalDate.now();
        Member member = createMember();
        ContentPost contentPost = createContentPost(member);
        List<DailyMemberViewLog> createdLogs = createViewLogs(member, contentPost, targetDate, 5);

        // ID 범위 설정
        long minId = createdLogs.get(0).getId();
        long maxId = createdLogs.get(2).getId(); // 처음 3개만 읽도록 설정

        // when
        JdbcPagingItemReader<DailyMemberViewLog> reader = dailyLogReader.reader(
                targetDate.toString(),
                minId,
                maxId
        );
        reader.afterPropertiesSet();

        // then
        List<DailyMemberViewLog> logs = new ArrayList<>();
        DailyMemberViewLog log;
        while ((log = reader.read()) != null) {
            logs.add(log);
        }

        assertThat(logs).hasSize(3);
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

    private List<DailyMemberViewLog> createViewLogs(Member member, ContentPost contentPost, LocalDate date, int count) {
        List<DailyMemberViewLog> logs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            DailyMemberViewLog viewLog = DailyMemberViewLog.builder()
                    .member(member)
                    .contentPost(contentPost)
                    .lastViewedPosition(100 * (i + 1))
                    .lastAdViewCount(i)
                    .logDate(date)
                    .status(StreamingStatus.COMPLETED)
                    .build();
            logs.add(viewLogRepository.save(viewLog));
        }
        return logs;
    }
}