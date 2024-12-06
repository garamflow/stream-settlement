package com.github.garamflow.streamsettlement.batch.reader;

import com.github.garamflow.streamsettlement.entity.stream.Log.DailyMemberViewLog;
import com.github.garamflow.streamsettlement.repository.log.DailyMemberViewLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@SpringBootTest
@SpringBatchTest
class DailyLogReaderTest {

    private static final int CHUNK_SIZE = 1000;

    @Autowired
    private DailyLogReader dailyLogReader;

    @MockBean
    private DailyMemberViewLogRepository viewLogRepository;

    @Test
    void 특정_날짜의_로그를_정상적으로_읽어온다() throws Exception {
        // given
        LocalDate targetDate = LocalDate.now();
        JdbcPagingItemReader<DailyMemberViewLog> mockReader = mock(JdbcPagingItemReader.class.asSubclass(JdbcPagingItemReader.class));
        when(viewLogRepository.createPagingReader(
                targetDate,
                1L,
                3L,
                CHUNK_SIZE
        )).thenReturn(mockReader);

        // when
        JdbcPagingItemReader<DailyMemberViewLog> reader = dailyLogReader.reader(
                targetDate.toString(),
                1L,
                3L
        );

        // then
        verify(viewLogRepository).createPagingReader(
                targetDate,
                1L,
                3L,
                CHUNK_SIZE
        );
        assertThat(reader).isNotNull();
    }

    @Test
    void 잘못된_날짜_형식이면_예외를_던진다() {
        // given
        String invalidDate = "2024-13-45";

        // when & then
        assertThatThrownBy(() ->
                dailyLogReader.reader(invalidDate, 1L, 3L)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid 'targetDate' format");
    }
}