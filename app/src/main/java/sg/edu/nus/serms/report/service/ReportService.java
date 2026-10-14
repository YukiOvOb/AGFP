package sg.edu.nus.serms.report.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.serms.maintenance.service.MaintenanceReportService;
import sg.edu.nus.serms.report.domain.OperationsReport;
import sg.edu.nus.serms.report.domain.OperationsReport.CategoryUtilisation;
import sg.edu.nus.serms.report.domain.OperationsReport.EquipmentUtilisation;
import sg.edu.nus.serms.report.domain.OperationsReport.MaintenanceSummary;
import sg.edu.nus.serms.report.repository.ReportRepository;
import sg.edu.nus.serms.report.repository.ReportRepository.LoanedTime;

/** Administrator reports (Proposal 5.7). Maintenance figures come from the UC04 report service. */
@Service
public class ReportService {
  /** Longest period one request may cover; keeps the queries well inside the 5-second target. */
  public static final Duration MAX_PERIOD = Duration.ofDays(366);
  static final int MAINTENANCE_ITEMS = 10;

  private final ReportRepository reports;
  private final MaintenanceReportService maintenance;
  private final Clock clock;

  public ReportService(
      ReportRepository reports,
      MaintenanceReportService maintenance,
      @Qualifier("clock") Clock clock) {
    this.reports = Objects.requireNonNull(reports);
    this.maintenance = Objects.requireNonNull(maintenance);
    this.clock = Objects.requireNonNull(clock);
  }

  // One snapshot for every section, so totals agree with the tables beside them.
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  @PreAuthorize("hasRole('ADMIN')")
  public OperationsReport report(Instant from, Instant to) {
    if (from == null || to == null || !from.isBefore(to))
      throw new IllegalArgumentException("The period must start before it ends");
    if (Duration.between(from, to).compareTo(MAX_PERIOD) > 0)
      throw new IllegalArgumentException("The period may cover at most 366 days");
    Instant now = clock.instant();
    Instant until = to.isAfter(now) ? now : to;
    // A period entirely in the future has nothing measured yet.
    Instant measuredFrom = from.isAfter(until) ? until : from;
    Duration measured = Duration.between(measuredFrom, until);

    List<EquipmentUtilisation> items =
        reports.loanedTime(measuredFrom, until).stream().map(t -> utilisation(t, measured)).toList();
    var snapshot = maintenance.getMaintenanceReport(from, to);
    var byStatus = new LinkedHashMap<String, Long>();
    snapshot.casesByStatus().forEach((status, count) -> byStatus.put(status.name(), count));
    return new OperationsReport(
        from,
        to,
        until,
        now,
        reports.reservationsByCategory(from, to),
        items,
        byCategory(items, measured),
        reports.overdueAt(now),
        reports.overdueByCategory(measuredFrom, until),
        new MaintenanceSummary(
            Map.copyOf(byStatus),
            snapshot.reportedCaseCount(),
            snapshot.completedCaseCount(),
            snapshot.averageCompletionDuration().orElse(null)),
        reports.maintenanceByEquipment(from, to, MAINTENANCE_ITEMS));
  }

  static EquipmentUtilisation utilisation(LoanedTime t, Duration measured) {
    return new EquipmentUtilisation(
        t.equipmentId(),
        t.assetTag(),
        t.name(),
        t.category(),
        t.status(),
        t.loans(),
        t.loanedTime(),
        ratio(t.loanedTime(), measured));
  }

  /** Category totals in the order of first appearance, which is busiest item first. */
  static List<CategoryUtilisation> byCategory(List<EquipmentUtilisation> items, Duration measured) {
    var groups = new LinkedHashMap<String, List<EquipmentUtilisation>>();
    items.forEach(i -> groups.computeIfAbsent(i.category(), k -> new ArrayList<>()).add(i));
    return groups.entrySet().stream()
        .map(
            e -> {
              Duration loaned =
                  e.getValue().stream().map(EquipmentUtilisation::loanedTime).reduce(Duration.ZERO, Duration::plus);
              return new CategoryUtilisation(
                  e.getKey(),
                  e.getValue().size(),
                  loaned,
                  ratio(loaned, measured.multipliedBy(e.getValue().size())));
            })
        .sorted((a, b) -> Double.compare(b.utilisation(), a.utilisation()))
        .toList();
  }

  /** Share of {@code available} that was {@code used}, between 0 and 1; 0 for an empty window. */
  static double ratio(Duration used, Duration available) {
    if (available.isZero() || available.isNegative()) return 0;
    return Math.min(1, (double) used.toMillis() / available.toMillis());
  }
}
