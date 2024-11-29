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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ExtendWith(SpringExtension.class)
class DailyLogProcessorTest {

    private DailyLogProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new DailyLogProcessor();
    }

    @Test
    void 완료된_시청로그를_통계로_변환한다() {
        // given
        LocalDate today = LocalDate.now();
        Member member = createMember();
        ContentPost contentPost = createContentPost(member);
        DailyMemberViewLog log = createCompletedLog(member, contentPost, today);

        // when
        ContentStatistics result = processor.process(log);

        // then
        assertThat(result).isNotNull()
                .satisfies(stat -> {
                    assertThat(stat.getContentPost()).isEqualTo(contentPost);
                    assertThat(stat.getStatisticsDate()).isEqualTo(today);
                    assertThat(stat.getPeriod()).isEqualTo(StatisticsPeriod.DAILY);
                    assertThat(stat.getViewCount()).isEqualTo(1L);
                    assertThat(stat.getWatchTime()).isEqualTo(100L);
                    assertThat(stat.getAccumulatedViews()).isEqualTo(contentPost.getTotalViews());
                });
    }

    @Test
    void 진행중인_시청로그는_처리하지_않는다() {
        // given
        Member member = createMember();
        ContentPost contentPost = createContentPost(member);
        DailyMemberViewLog log = createInProgressLog(member, contentPost, LocalDate.now());

        // when
        ContentStatistics result = processor.process(log);

        // then
        assertThat(result).isNull();
    }

    @Test
    void ContentPost가_null이면_예외를_던진다() {
        // given
        Member member = createMember();
        ContentPost contentPost = createContentPost(member);
        DailyMemberViewLog log = createCompletedLog(member, contentPost, LocalDate.now());

        // Reflection을 사용하여 contentPost를 null로 설정
        ReflectionTestUtils.setField(log, "contentPost", null);

        // when & then
        assertThatThrownBy(() -> processor.process(log))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ContentPost");
    }


    @Test
    void Member가_null이면_예외를_던진다() {
        // given
        Member member = createMember();
        ContentPost contentPost = createContentPost(member);
        DailyMemberViewLog log = createCompletedLog(member, contentPost, LocalDate.now());

        // Reflection을 사용하여 member를 null로 설정
        ReflectionTestUtils.setField(log, "member", null);

        // 설정 확인
        assertThat(log.getMember()).isNull(); // member가 null인지 확인

        // when & then
        assertThatThrownBy(() -> processor.process(log))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DailyMemberViewLog contains null fields: member is null");
    }


    @Test
    void 다른_상태의_시청로그는_처리하지_않는다() {
        // given
        Member member = createMember();
        ContentPost contentPost = createContentPost(member);
        DailyMemberViewLog pausedLog = createLogWithStatus(member, contentPost, StreamingStatus.PAUSED);
        DailyMemberViewLog canceledLog = createLogWithStatus(member, contentPost, StreamingStatus.STOPPED);

        // when
        ContentStatistics pausedResult = processor.process(pausedLog);
        ContentStatistics canceledResult = processor.process(canceledLog);

        // then
        assertThat(pausedResult).isNull();
        assertThat(canceledResult).isNull();
    }

    private DailyMemberViewLog createLogWithStatus(Member member, ContentPost contentPost, StreamingStatus status) {
        return DailyMemberViewLog.builder()
                .member(member)
                .contentPost(contentPost)
                .lastViewedPosition(0)
                .lastAdViewCount(0)
                .logDate(LocalDate.now())
                .status(status)
                .build();
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