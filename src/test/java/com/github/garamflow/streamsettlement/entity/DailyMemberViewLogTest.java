package com.github.garamflow.streamsettlement.entity;

import com.github.garamflow.streamsettlement.entity.member.Member;
import com.github.garamflow.streamsettlement.entity.member.Role;
import com.github.garamflow.streamsettlement.entity.stream.Log.DailyMemberViewLog;
import com.github.garamflow.streamsettlement.entity.stream.Log.StreamingStatus;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ExtendWith(SpringExtension.class)
class DailyMemberViewLogTest {

    private Member member;
    private ContentPost contentPost;

    @BeforeEach
    void setUp() {
        member = new Member.Builder()
                .email("test@example.com")
                .role(Role.MEMBER)
                .build();

        contentPost = ContentPost.builder()
                .member(member)
                .title("테스트 컨텐츠")
                .url("https://example.com/test")
                .build();
    }

    @Test
    void 정상적인_로그를_생성한다() {
        // given
        LocalDate today = LocalDate.now();

        // when
        DailyMemberViewLog log = DailyMemberViewLog.builder()
                .member(member)
                .contentPost(contentPost)
                .lastViewedPosition(100)
                .lastAdViewCount(2)
                .logDate(today)
                .status(StreamingStatus.COMPLETED)
                .build();

        // then
        assertThat(log).isNotNull()
                .satisfies(l -> {
                    assertThat(l.getMember()).isEqualTo(member);
                    assertThat(l.getContentPost()).isEqualTo(contentPost);
                    assertThat(l.getLastViewedPosition()).isEqualTo(100);
                    assertThat(l.getLastAdViewCount()).isEqualTo(2);
                    assertThat(l.getLogDate()).isEqualTo(today);
                    assertThat(l.getStatus()).isEqualTo(StreamingStatus.COMPLETED);
                });
    }

    @Test
    void Member가_null이면_예외를_던진다() {
        assertThatThrownBy(() -> DailyMemberViewLog.builder()
                .member(null)
                .contentPost(contentPost)
                .lastViewedPosition(100)
                .logDate(LocalDate.now())
                .status(StreamingStatus.COMPLETED)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Member is required");
    }

    @Test
    void ContentPost가_null이면_예외를_던진다() {
        assertThatThrownBy(() -> DailyMemberViewLog.builder()
                .member(member)
                .contentPost(null)
                .lastViewedPosition(100)
                .logDate(LocalDate.now())
                .status(StreamingStatus.COMPLETED)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ContentPost is required");
    }

    @Test
    void 시청_위치를_업데이트한다() {
        // given
        DailyMemberViewLog log = createLog(StreamingStatus.IN_PROGRESS);
        int newPosition = 200;

        // when
        log.updatePosition(newPosition, StreamingStatus.COMPLETED);

        // then
        assertThat(log.getLastViewedPosition()).isEqualTo(newPosition);
        assertThat(log.getStatus()).isEqualTo(StreamingStatus.COMPLETED);
    }

    @Test
    void 이전_위치보다_작은_위치로_업데이트하면_예외를_던진다() {
        // given
        DailyMemberViewLog log = createLog(StreamingStatus.IN_PROGRESS);
        int smallerPosition = 50;

        // when & then
        assertThatThrownBy(() -> log.updatePosition(smallerPosition, StreamingStatus.IN_PROGRESS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Position cannot be less than the last viewed position");
    }

    private DailyMemberViewLog createLog(StreamingStatus status) {
        return DailyMemberViewLog.builder()
                .member(member)
                .contentPost(contentPost)
                .lastViewedPosition(100)
                .lastAdViewCount(2)
                .logDate(LocalDate.now())
                .status(status)
                .build();
    }
}
