package com.github.garamflow.streamsettlement.batch.reader;

import com.github.garamflow.streamsettlement.entity.member.Member;
import com.github.garamflow.streamsettlement.entity.member.Role;
import com.github.garamflow.streamsettlement.entity.stream.Log.DailyMemberViewLog;
import com.github.garamflow.streamsettlement.entity.stream.Log.StreamingStatus;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import com.github.garamflow.streamsettlement.repository.statistics.ContentStatisticsRepository;
import com.github.garamflow.streamsettlement.repository.stream.ContentPostRepository;
import com.github.garamflow.streamsettlement.repository.stream.DailyMemberViewLogRepository;
import com.github.garamflow.streamsettlement.repository.user.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ExtendWith(SpringExtension.class)
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

    @BeforeEach
    void setUp() {
        viewLogRepository.deleteAll();
        contentStatisticsRepository.deleteAll();
        contentPostRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    void reader는_특정_날짜의_로그를_모두_읽어온다() throws Exception {
        // given
        LocalDate targetDate = LocalDate.of(2024, 3, 20);
        Member member = createMember();
        ContentPost contentPost = createContentPost(member);
        createViewLogs(member, contentPost, targetDate);

        JdbcPagingItemReader<DailyMemberViewLog> reader = dailyLogReader.reader(targetDate.toString());
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