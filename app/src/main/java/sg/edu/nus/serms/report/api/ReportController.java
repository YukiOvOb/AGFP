package sg.edu.nus.serms.report.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sg.edu.nus.serms.report.domain.OperationsReport;
import sg.edu.nus.serms.report.service.ReportService;

/** Administrator reports (Proposal 5.7). Durations are sent as hours for charting. */
@RestController
@RequestMapping(value = "/api/v1/reports", produces = "application/json")
public class ReportController {
  private final ReportService service;

  public ReportController(ReportService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(
      operationId = "getOperationsReport",
      summary = "Reservations, utilisation, overdue and maintenance figures for one period")
  public ReportView report(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
    return ReportView.of(service.report(from, to));
  }

  static double hours(Duration d) {
    return d == null ? 0 : d.toMillis() / 3_600_000.0;
  }

  public record ReportView(
      @Schema(requiredMode = RequiredMode.REQUIRED) Instant from,
      @Schema(requiredMode = RequiredMode.REQUIRED) Instant to,
      @Schema(
              requiredMode = RequiredMode.REQUIRED,
              description = "Utilisation and overdue frequency are measured up to this time")
          Instant measuredUntil,
      @Schema(requiredMode = RequiredMode.REQUIRED) Instant generatedAt,
      @Schema(requiredMode = RequiredMode.REQUIRED) List<CategoryReservationsView> reservationsByCategory,
      @Schema(requiredMode = RequiredMode.REQUIRED) List<EquipmentUtilisationView> equipmentUtilisation,
      @Schema(requiredMode = RequiredMode.REQUIRED) List<CategoryUtilisationView> utilisationByCategory,
      @Schema(requiredMode = RequiredMode.REQUIRED) List<OverdueLoanView> overdueNow,
      @Schema(requiredMode = RequiredMode.REQUIRED) List<CategoryOverdueView> overdueByCategory,
      @Schema(requiredMode = RequiredMode.REQUIRED) MaintenanceView maintenance,
      @Schema(requiredMode = RequiredMode.REQUIRED) List<EquipmentMaintenanceView> maintenanceByEquipment) {
    static ReportView of(OperationsReport r) {
      return new ReportView(
          r.from(),
          r.to(),
          r.measuredUntil(),
          r.generatedAt(),
          r.reservationsByCategory().stream()
              .map(
                  c ->
                      new CategoryReservationsView(
                          c.category(),
                          c.total(),
                          c.pendingApproval(),
                          c.confirmed(),
                          c.fulfilled(),
                          c.rejected(),
                          c.cancelled()))
              .toList(),
          r.equipmentUtilisation().stream()
              .map(
                  e ->
                      new EquipmentUtilisationView(
                          e.equipmentId(),
                          e.assetTag(),
                          e.name(),
                          e.category(),
                          e.status(),
                          e.loans(),
                          hours(e.loanedTime()),
                          e.utilisation()))
              .toList(),
          r.utilisationByCategory().stream()
              .map(
                  c ->
                      new CategoryUtilisationView(
                          c.category(), c.equipment(), hours(c.loanedTime()), c.utilisation()))
              .toList(),
          r.overdueNow().stream()
              .map(
                  o ->
                      new OverdueLoanView(
                          o.loanId(),
                          o.equipmentId(),
                          o.assetTag(),
                          o.equipmentName(),
                          o.category(),
                          o.borrowerName(),
                          o.dueAt(),
                          hours(o.overdueBy())))
              .toList(),
          r.overdueByCategory().stream()
              .map(
                  c ->
                      new CategoryOverdueView(
                          c.category(), c.loansDue(), c.returnedLate(), c.stillOverdue()))
              .toList(),
          new MaintenanceView(
              r.maintenance().casesByStatus(),
              r.maintenance().reportedInPeriod(),
              r.maintenance().completedInPeriod(),
              r.maintenance().averageCompletion() == null
                  ? null
                  : hours(r.maintenance().averageCompletion())),
          r.maintenanceByEquipment().stream()
              .map(
                  m ->
                      new EquipmentMaintenanceView(
                          m.equipmentId(), m.assetTag(), m.name(), m.category(), m.cases()))
              .toList());
    }
  }

  public record CategoryReservationsView(
      @Schema(requiredMode = RequiredMode.REQUIRED) String category,
      @Schema(requiredMode = RequiredMode.REQUIRED) long total,
      @Schema(requiredMode = RequiredMode.REQUIRED) long pendingApproval,
      @Schema(requiredMode = RequiredMode.REQUIRED) long confirmed,
      @Schema(requiredMode = RequiredMode.REQUIRED) long fulfilled,
      @Schema(requiredMode = RequiredMode.REQUIRED) long rejected,
      @Schema(requiredMode = RequiredMode.REQUIRED) long cancelled) {}

  public record EquipmentUtilisationView(
      @Schema(requiredMode = RequiredMode.REQUIRED) UUID equipmentId,
      @Schema(requiredMode = RequiredMode.REQUIRED) String assetTag,
      @Schema(requiredMode = RequiredMode.REQUIRED) String name,
      @Schema(requiredMode = RequiredMode.REQUIRED) String category,
      @Schema(requiredMode = RequiredMode.REQUIRED) String status,
      @Schema(requiredMode = RequiredMode.REQUIRED) long loans,
      @Schema(requiredMode = RequiredMode.REQUIRED) double loanedHours,
      @Schema(requiredMode = RequiredMode.REQUIRED, description = "0 to 1") double utilisation) {}

  public record CategoryUtilisationView(
      @Schema(requiredMode = RequiredMode.REQUIRED) String category,
      @Schema(requiredMode = RequiredMode.REQUIRED) int equipment,
      @Schema(requiredMode = RequiredMode.REQUIRED) double loanedHours,
      @Schema(requiredMode = RequiredMode.REQUIRED, description = "0 to 1") double utilisation) {}

  public record OverdueLoanView(
      @Schema(requiredMode = RequiredMode.REQUIRED) UUID loanId,
      @Schema(requiredMode = RequiredMode.REQUIRED) UUID equipmentId,
      @Schema(requiredMode = RequiredMode.REQUIRED) String assetTag,
      @Schema(requiredMode = RequiredMode.REQUIRED) String equipmentName,
      @Schema(requiredMode = RequiredMode.REQUIRED) String category,
      @Schema(requiredMode = RequiredMode.REQUIRED) String borrowerName,
      @Schema(requiredMode = RequiredMode.REQUIRED) Instant dueAt,
      @Schema(requiredMode = RequiredMode.REQUIRED) double overdueHours) {}

  public record CategoryOverdueView(
      @Schema(requiredMode = RequiredMode.REQUIRED) String category,
      @Schema(requiredMode = RequiredMode.REQUIRED) long loansDue,
      @Schema(requiredMode = RequiredMode.REQUIRED) long returnedLate,
      @Schema(requiredMode = RequiredMode.REQUIRED) long stillOverdue) {}

  public record MaintenanceView(
      @Schema(requiredMode = RequiredMode.REQUIRED, description = "Current cases by stored status")
          Map<String, Long> casesByStatus,
      @Schema(requiredMode = RequiredMode.REQUIRED) long reportedInPeriod,
      @Schema(requiredMode = RequiredMode.REQUIRED) long completedInPeriod,
      @Schema(description = "Absent when no case was completed in the period")
          Double averageCompletionHours) {}

  public record EquipmentMaintenanceView(
      @Schema(requiredMode = RequiredMode.REQUIRED) UUID equipmentId,
      @Schema(requiredMode = RequiredMode.REQUIRED) String assetTag,
      @Schema(requiredMode = RequiredMode.REQUIRED) String name,
      @Schema(requiredMode = RequiredMode.REQUIRED) String category,
      @Schema(requiredMode = RequiredMode.REQUIRED) long cases) {}
}
