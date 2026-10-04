package sg.edu.nus.serms.maintenance.service;

import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.serms.maintenance.repository.MaintenanceReportRepository;

/** Reporting orchestration is separate from case mutation and aggregate queries. */
@Service
public class MaintenanceReportService {
  private final MaintenanceReportRepository reports;

  public MaintenanceReportService(MaintenanceReportRepository reports) {
    this.reports = Objects.requireNonNull(reports, "reports");
  }

  // All four primitives must observe the same PostgreSQL snapshot despite concurrent completion.
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public MaintenanceReportSnapshot getMaintenanceReport(Instant fromInclusive, Instant toExclusive) {
    MaintenanceReportSnapshot.validateRange(fromInclusive, toExclusive);
    return new MaintenanceReportSnapshot(fromInclusive, toExclusive, reports.countCasesByStatus(),
        reports.countReportedBetween(fromInclusive, toExclusive),
        reports.countCompletedBetween(fromInclusive, toExclusive),
        reports.averageCompletionDurationBetween(fromInclusive, toExclusive));
  }
}
