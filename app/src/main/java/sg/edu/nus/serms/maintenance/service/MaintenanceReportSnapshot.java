package sg.edu.nus.serms.maintenance.service;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import sg.edu.nus.serms.maintenance.domain.MaintenanceStatus;

/**
 * Immutable backend reporting primitives. Status counts cover all cases, independently of the
 * interval. Reported count is a neutral input for future frequency reporting, not a final UI
 * definition. Interval statistics use caller-supplied instants without calendar/timezone defaults.
 */
public record MaintenanceReportSnapshot(
    Instant fromInclusive,
    Instant toExclusive,
    Map<MaintenanceStatus, Long> casesByStatus,
    long reportedCaseCount,
    long completedCaseCount,
    Optional<Duration> averageCompletionDuration) {

  public MaintenanceReportSnapshot {
    validateRange(fromInclusive, toExclusive);
    Objects.requireNonNull(casesByStatus, "casesByStatus");
    var counts = new EnumMap<MaintenanceStatus, Long>(MaintenanceStatus.class);
    for (var status : MaintenanceStatus.values()) counts.put(status, 0L);
    casesByStatus.forEach((status, count) -> {
      Objects.requireNonNull(status, "status");
      Objects.requireNonNull(count, "status count");
      if (count < 0) throw new IllegalArgumentException("Negative status count");
      counts.put(status, count);
    });
    casesByStatus = Map.copyOf(counts);
    if (reportedCaseCount < 0 || completedCaseCount < 0) {
      throw new IllegalArgumentException("Negative case count");
    }
    Objects.requireNonNull(averageCompletionDuration, "averageCompletionDuration");
    if ((completedCaseCount == 0) != averageCompletionDuration.isEmpty()) {
      throw new IllegalArgumentException("Average duration must be present exactly when completed cases exist");
    }
    averageCompletionDuration.ifPresent(duration -> {
      if (duration.isNegative()) throw new IllegalArgumentException("Negative completion duration");
    });
  }

  static void validateRange(Instant fromInclusive, Instant toExclusive) {
    Objects.requireNonNull(fromInclusive, "fromInclusive");
    Objects.requireNonNull(toExclusive, "toExclusive");
    if (!fromInclusive.isBefore(toExclusive)) {
      throw new IllegalArgumentException("fromInclusive must be before toExclusive");
    }
  }
}
