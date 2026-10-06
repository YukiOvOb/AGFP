package sg.edu.nus.serms.maintenance.repository;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import sg.edu.nus.serms.maintenance.domain.MaintenanceStatus;

/** Read-only reporting port. Every interval is [fromInclusive, toExclusive), with no timezone defaults. */
public interface MaintenanceReportRepository {
  /** All cases, not interval-filtered; immutable counts including zero for every canonical status. */
  Map<MaintenanceStatus, Long> countCasesByStatus();

  /** Neutral frequency primitive: number reported within the caller's explicit interval. */
  long countReportedBetween(Instant fromInclusive, Instant toExclusive);

  /** RESOLVED/UNREPAIRABLE cases whose resolved_at falls in the interval. */
  long countCompletedBetween(Instant fromInclusive, Instant toExclusive);

  /** Mean resolved_at - reported_at for the same completed set; empty when that set is empty. */
  Optional<Duration> averageCompletionDurationBetween(Instant fromInclusive, Instant toExclusive);
}
