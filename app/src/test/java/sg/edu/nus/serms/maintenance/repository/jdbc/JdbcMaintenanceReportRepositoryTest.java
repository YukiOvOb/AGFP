package sg.edu.nus.serms.maintenance.repository.jdbc;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;
import sg.edu.nus.serms.maintenance.domain.MaintenanceStatus;

class JdbcMaintenanceReportRepositoryTest {
  private static final Instant FROM = Instant.parse("2026-10-04T00:00:00.123456Z"), TO = FROM.plusSeconds(60);
  private final JdbcOperations jdbc = mock(JdbcOperations.class);
  private final JdbcMaintenanceReportRepository repository = new JdbcMaintenanceReportRepository(jdbc);
  private final ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);

  private void statusRows(Map<MaintenanceStatus, Long> counts) throws Exception {
    var rows = new ArrayList<ResultSet>();
    for (var entry : counts.entrySet()) {
      var row = mock(ResultSet.class);
      when(row.getString("status")).thenReturn(entry.getKey().name());
      when(row.getLong("case_count")).thenReturn(entry.getValue());
      rows.add(row);
    }
    when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Map.Entry<MaintenanceStatus, Long>>>any()))
        .thenAnswer(call -> {
          RowMapper<Map.Entry<MaintenanceStatus, Long>> mapper = call.getArgument(1);
          var result = new ArrayList<Map.Entry<MaintenanceStatus, Long>>();
          for (int i = 0; i < rows.size(); i++) result.add(mapper.mapRow(rows.get(i), i));
          return result;
        });
  }

  @Test
  void mapsAllCanonicalStatusesAndLongCountsWithGlobalGroupBy() throws Exception {
    var expected = new EnumMap<MaintenanceStatus, Long>(MaintenanceStatus.class);
    for (var status : MaintenanceStatus.values()) expected.put(status, 4_294_967_297L + status.ordinal());
    statusRows(expected);
    var counts = repository.countCasesByStatus();
    assertThat(counts).isEqualTo(expected);
    assertThatThrownBy(counts::clear).isInstanceOf(UnsupportedOperationException.class);
    verify(jdbc).query(sql.capture(), org.mockito.ArgumentMatchers.<RowMapper<Map.Entry<MaintenanceStatus, Long>>>any());
    assertThat(normalizedSql()).isEqualTo("SELECT status, COUNT(*) AS case_count FROM serms.maintenance_case GROUP BY status");
  }

  @Test
  void fillsMissingGroupByStatusesWithZero() throws Exception {
    statusRows(Map.of(MaintenanceStatus.RESOLVED, 3L));
    var counts = repository.countCasesByStatus();
    assertThat(counts).containsOnlyKeys(MaintenanceStatus.values()).containsEntry(MaintenanceStatus.RESOLVED, 3L);
    for (var status : MaintenanceStatus.values()) {
      if (status != MaintenanceStatus.RESOLVED) assertThat(counts.get(status)).isZero();
    }
  }

  @Test
  void emptyTableStillReturnsAllFiveZeroCounts() throws Exception {
    statusRows(Map.of());
    assertThat(repository.countCasesByStatus()).hasSize(5).containsOnlyKeys(MaintenanceStatus.values());
    assertThat(repository.countCasesByStatus().values()).containsOnly(0L);
  }

  @Test
  void reportedCountUsesReportedTimeInclusiveLowerAndExclusiveUpper() {
    when(jdbc.queryForObject(anyString(), eq(Long.class), eq(Timestamp.from(FROM)), eq(Timestamp.from(TO))))
        .thenReturn(4_294_967_297L);
    assertThat(repository.countReportedBetween(FROM, TO)).isEqualTo(4_294_967_297L);
    verify(jdbc).queryForObject(sql.capture(), eq(Long.class), eq(Timestamp.from(FROM)), eq(Timestamp.from(TO)));
    assertThat(normalizedSql()).isEqualTo(
        "SELECT COUNT(*) FROM serms.maintenance_case WHERE reported_at >= ? AND reported_at < ?");
  }

  @Test
  void completedCountUsesResolvedTimeAndOnlyBothTerminalStates() {
    when(jdbc.queryForObject(anyString(), eq(Long.class), eq(Timestamp.from(FROM)), eq(Timestamp.from(TO))))
        .thenReturn(2L);
    assertThat(repository.countCompletedBetween(FROM, TO)).isEqualTo(2);
    verify(jdbc).queryForObject(sql.capture(), eq(Long.class), eq(Timestamp.from(FROM)), eq(Timestamp.from(TO)));
    assertThat(normalizedSql()).isEqualTo("SELECT COUNT(*) " + completedPredicate());
  }

  static Stream<Arguments> numericDurations() {
    return Stream.of(
        Arguments.of("0", Duration.ZERO),
        Arguments.of("12.75", Duration.ofSeconds(12, 750_000_000)),
        Arguments.of("1.123456789", Duration.ofSeconds(1, 123_456_789)),
        Arguments.of("0.0000003333333333", Duration.ofNanos(333)),
        Arguments.of("0.0000000005", Duration.ofNanos(1)),
        Arguments.of("0.9999999995", Duration.ofSeconds(1)),
        Arguments.of("12345678901.123456", Duration.ofSeconds(12_345_678_901L, 123_456_000)));
  }

  @ParameterizedTest
  @MethodSource("numericDurations")
  void numericAveragePreservesSubSecondPrecisionAndRoundsOnlyAtNanoseconds(String seconds, Duration expected) {
    when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), eq(Timestamp.from(FROM)), eq(Timestamp.from(TO))))
        .thenReturn(new BigDecimal(seconds));
    assertThat(repository.averageCompletionDurationBetween(FROM, TO)).contains(expected);
    verify(jdbc).queryForObject(sql.capture(), eq(BigDecimal.class), eq(Timestamp.from(FROM)), eq(Timestamp.from(TO)));
    assertThat(normalizedSql()).isEqualTo(
        "SELECT AVG(EXTRACT(EPOCH FROM (resolved_at - reported_at))) " + completedPredicate());
    assertThat(normalizedSql()).doesNotContain("NOW", "CURRENT_TIMESTAMP", "COALESCE");
  }

  @Test
  void nullPostgresAverageForNoCompletedRowsReturnsEmpty() {
    when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), eq(Timestamp.from(FROM)), eq(Timestamp.from(TO))))
        .thenReturn(null);
    assertThat(repository.averageCompletionDurationBetween(FROM, TO)).isEmpty();
    verify(jdbc).queryForObject(sql.capture(), eq(BigDecimal.class), eq(Timestamp.from(FROM)), eq(Timestamp.from(TO)));
    assertThat(normalizedSql()).endsWith(completedPredicate());
  }

  private String normalizedSql() { return sql.getValue().replaceAll("\\s+", " ").strip(); }

  private String completedPredicate() {
    return "FROM serms.maintenance_case WHERE resolved_at >= ? AND resolved_at < ? AND status IN ('RESOLVED', 'UNREPAIRABLE')";
  }
}
