package sg.edu.nus.serms.maintenance.repository.jdbc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Repository;
import sg.edu.nus.serms.maintenance.domain.MaintenanceStatus;
import sg.edu.nus.serms.maintenance.repository.MaintenanceReportRepository;

@Repository
public class JdbcMaintenanceReportRepository implements MaintenanceReportRepository {
  private static final String COMPLETED_PERIOD = """
       FROM serms.maintenance_case
      WHERE resolved_at >= ? AND resolved_at < ?
        AND status IN ('RESOLVED', 'UNREPAIRABLE')
      """;
  private final JdbcOperations jdbc;

  public JdbcMaintenanceReportRepository(JdbcOperations jdbc) { this.jdbc = jdbc; }

  @Override
  public Map<MaintenanceStatus, Long> countCasesByStatus() {
    var counts = new EnumMap<MaintenanceStatus, Long>(MaintenanceStatus.class);
    for (var status : MaintenanceStatus.values()) counts.put(status, 0L);
    var rows = jdbc.query("""
        SELECT status, COUNT(*) AS case_count FROM serms.maintenance_case GROUP BY status
        """, (row, index) -> Map.entry(MaintenanceStatus.valueOf(row.getString("status")), row.getLong("case_count")));
    rows.forEach(entry -> counts.put(entry.getKey(), entry.getValue()));
    return Map.copyOf(counts);
  }

  @Override
  public long countReportedBetween(Instant fromInclusive, Instant toExclusive) {
    return jdbc.queryForObject("""
        SELECT COUNT(*) FROM serms.maintenance_case WHERE reported_at >= ? AND reported_at < ?
        """, Long.class, Timestamp.from(fromInclusive), Timestamp.from(toExclusive));
  }

  @Override
  public long countCompletedBetween(Instant fromInclusive, Instant toExclusive) {
    return jdbc.queryForObject("SELECT COUNT(*)" + COMPLETED_PERIOD, Long.class,
        Timestamp.from(fromInclusive), Timestamp.from(toExclusive));
  }

  @Override
  public Optional<Duration> averageCompletionDurationBetween(Instant fromInclusive, Instant toExclusive) {
    BigDecimal seconds = jdbc.queryForObject(
        "SELECT AVG(EXTRACT(EPOCH FROM (resolved_at - reported_at)))" + COMPLETED_PERIOD,
        BigDecimal.class, Timestamp.from(fromInclusive), Timestamp.from(toExclusive));
    if (seconds == null) return Optional.empty();
    // PostgreSQL returns numeric, not a floating-point approximation. Round only at Duration's
    // nanosecond resolution, retaining sub-second averages. Split before converting to long so
    // long durations do not overflow a single total-nanoseconds value.
    BigDecimal nanos = seconds.movePointRight(9).setScale(0, RoundingMode.HALF_UP);
    BigDecimal[] parts = nanos.divideAndRemainder(BigDecimal.valueOf(1_000_000_000L));
    return Optional.of(Duration.ofSeconds(parts[0].longValueExact(), parts[1].longValueExact()));
  }
}
