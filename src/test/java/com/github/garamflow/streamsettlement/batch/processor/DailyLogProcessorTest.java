package com.github.garamflow.streamsettlement.batch.processor;

import com.github.garamflow.streamsettlement.entity.statistics.ContentStatistics;
import com.github.garamflow.streamsettlement.entity.statistics.StatisticsPeriod;
import com.github.garamflow.streamsettlement.entity.stream.Log.MemberContentWatchLog;
import com.github.garamflow.streamsettlement.entity.stream.Log.StreamingStatus;
import com.github.garamflow.streamsettlement.entity.stream.content.ContentPost;
import com.github.garamflow.streamsettlement.repository.stream.ContentPostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyLogProcessorTest {
    @Mock
    private ContentPostRepository contentPostRepository;

    @InjectMocks
    private DailyLogProcessor processor;

    private final LocalDate targetDate = LocalDate.of(2024, 1, 1);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(processor, "targetDate", targetDate);
    }

    @Test
    @DisplayName("유효한 시청 로그 처리")
    void processValidWatchLog() throws Exception {
        // given
        ContentPost contentPost = createContentPost(1L, 100L);
        MemberContentWatchLog watchLog = createValidWatchLog(1L);

        when(contentPostRepository.findById(1L))
                .thenReturn(Optional.of(contentPost));

        // when
        List<ContentStatistics> result = processor.process(watchLog);

        // then
        assertThat(result)
                .hasSize(1)
                .first()
                .satisfies(statistics -> {
                    assertThat(statistics.getContentPost()).isEqualTo(contentPost);
                    assertThat(statistics.getStatisticsDate()).isEqualTo(targetDate);
                    assertThat(statistics.getPeriod()).isEqualTo(StatisticsPeriod.DAILY);
                    assertThat(statistics.getViewCount()).isEqualTo(1L);
                    assertThat(statistics.getWatchTime()).isEqualTo(watchLog.getTotalPlaybackTime());
                    assertThat(statistics.getAccumulatedViews()).isEqualTo(contentPost.getTotalViews());
                });
    }

    @Test
    @DisplayName("동일한 컨텐츠의 시청 로그 중복 처리")
    void processDuplicateWatchLog() throws Exception {
        // given
        ContentPost contentPost = createContentPost(1L, 100L);
        MemberContentWatchLog watchLog1 = createValidWatchLog(1L);
        MemberContentWatchLog watchLog2 = createValidWatchLog(1L);

        when(contentPostRepository.findById(1L))
                .thenReturn(Optional.of(contentPost));

        // when
        processor.process(watchLog1);
        List<ContentStatistics> result = processor.process(watchLog2);

        // then
        assertThat(result)
                .hasSize(1)
                .first()
                .satisfies(statistics -> {
                    assertThat(statistics.getViewCount()).isEqualTo(2L);
                    assertThat(statistics.getWatchTime())
                            .isEqualTo(watchLog1.getTotalPlaybackTime() + watchLog2.getTotalPlaybackTime());
                });
    }

    @Test
    @DisplayName("컨텐츠를 을 수 없을 때 예외 발생")
    void processNotFoundContent() {
        // given
        MemberContentWatchLog watchLog = createValidWatchLog(999L);
        when(contentPostRepository.findById(999L))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> processor.process(watchLog))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("ContentPost not found");
    }

    @Test
    @DisplayName("유효하지 않은 시청 로그 처리")
    void processInvalidWatchLog() throws Exception {
        // given
        MemberContentWatchLog nullLog = null;
        MemberContentWatchLog incompleteLog = createInvalidWatchLog(StreamingStatus.IN_PROGRESS);
        MemberContentWatchLog invalidPositionLog = createInvalidWatchLog(0L);

        // when & then
        assertThat(processor.process(nullLog)).isNull();
        assertThat(processor.process(incompleteLog)).isNull();
        assertThat(processor.process(invalidPositionLog)).isNull();
    }

    private ContentPost createContentPost(Long id, Long totalViews) {
        ContentPost contentPost = ContentPost.builder()
                .title("test title")
                .url("test url")
                .totalViews(totalViews)
                .build();
        ReflectionTestUtils.setField(contentPost, "id", id);
        return contentPost;
    }

    private MemberContentWatchLog createValidWatchLog(Long contentPostId) {
        return MemberContentWatchLog.customBuilder()
                .memberId(1L)
                .contentPostId(contentPostId)
                .lastPlaybackPosition(100L)
                .totalPlaybackTime(90L)
                .watchedDate(targetDate)
                .streamingStatus(StreamingStatus.COMPLETED)
                .build();
    }

    private MemberContentWatchLog createInvalidWatchLog(StreamingStatus status) {
        return MemberContentWatchLog.customBuilder()
                .memberId(1L)
                .contentPostId(1L)
                .lastPlaybackPosition(100L)
                .totalPlaybackTime(90L)
                .watchedDate(targetDate)
                .streamingStatus(status)
                .build();
    }

    private MemberContentWatchLog createInvalidWatchLog(Long position) {
        return MemberContentWatchLog.customBuilder()
                .memberId(1L)
                .contentPostId(1L)
                .lastPlaybackPosition(position)
                .totalPlaybackTime(90L)
                .watchedDate(targetDate)
                .streamingStatus(StreamingStatus.COMPLETED)
                .build();
    }
} 