package com.github.garamflow.streamsettlement.batch.reader;

import com.github.garamflow.streamsettlement.batch.config.BatchProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

@SpringBootTest
@SpringBatchTest
@ExtendWith(MockitoExtension.class)
class DailyLogReaderTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private BatchProperties batchProperties;

    @Mock
    private DataSource dataSource;

    @InjectMocks
    private DailyLogReader reader;

    private final LocalDate targetDate = LocalDate.of(2024, 1, 1);

    @BeforeEach
    void setUp() {
        lenient().when(jdbcTemplate.getDataSource()).thenReturn(dataSource);
        lenient().when(batchProperties.getChunkSize()).thenReturn(1000);
    }

    @Test
    @DisplayName("createDelegate() - ContentId가 null일 때 예외 발생")
    void createDelegateFail_NullContentId() {
        assertThatThrownBy(() ->
                reader.createDelegate(targetDate.toString(), null, 10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("StepExecutionContext missing 'startContentId' or 'endContentId'.");
    }

    @Test
    @DisplayName("createDelegate() - 잘못된 날짜 형식일 때 예외 발생")
    void createDelegateFail_InvalidDateFormat() {
        assertThatThrownBy(() ->
                reader.createDelegate("invalid-date", 1L, 10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid 'targetDate' format. Expected format is yyyy-MM-dd.");
    }

    @Test
    @DisplayName("createDelegate() - 날짜가 null일 때 예외 발생")
    void createDelegateFail_NullDate() {
        assertThatThrownBy(() ->
                reader.createDelegate(null, 1L, 10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Job parameter 'targetDate' is required but not provided.");
    }
}