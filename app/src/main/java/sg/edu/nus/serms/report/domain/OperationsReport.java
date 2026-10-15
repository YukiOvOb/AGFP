package sg.edu.nus.serms.report.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Administrator reports for one period (Proposal 5.7). Every list is read in one database snapshot.
 * "Overdue" means an {@code ACTIVE} loan whose {@code due_at} has passed, the same definition the
 * reservation rule and the UC05 overdue view use.
 *
 * @param from start of the period, inclusive
 * @param to end of the period, exclusive
 * @param measuredUntil end of the part of the period that has already happened: the earlier of
 *     {@code to} and the time of the report; utilisation and overdue frequency stop there
 */
public record OperationsReport(
    Instant from,
    Instant to,
    Instant measuredUntil,
    Instant generatedAt,
    List<CategoryReservations> reservationsByCategory,
    List<EquipmentUtilisation> equipmentUtilisation,
    List<CategoryUtilisation> utilisationByCategory,
    List<OverdueLoan> overdueNow,
    List<CategoryOverdue> overdueByCategory,
    MaintenanceSummary maintenance,
    List<EquipmentMaintenance> maintenanceByEquipment) {

  /** Reservations whose slot starts in the period, by equipment category and status. */
  public record CategoryReservations(
      String category,
      long total,
      long pendingApproval,
      long confirmed,
      long fulfilled,
      long rejected,
      long cancelled) {}

  /** Time on loan inside the measured part of the period, for one item. */
  public record EquipmentUtilisation(
      UUID equipmentId,
      String assetTag,
      String name,
      String category,
      String status,
      long loans,
      Duration loanedTime,
      double utilisation) {}

  /** Loaned time of a category divided by the time all its listed items were available. */
  public record CategoryUtilisation(
      String category, int equipment, Duration loanedTime, double utilisation) {}

  /** A loan that is overdue at the time of the report. */
  public record OverdueLoan(
      UUID loanId,
      UUID equipmentId,
      String assetTag,
      String equipmentName,
      String category,
      UUID borrowerId,
      String borrowerName,
      Instant dueAt,
      Duration overdueBy) {}

  /**
   * Loans that fell due in the measured part of the period: how many came back late and how many
   * are still out.
   */
  public record CategoryOverdue(
      String category, long loansDue, long returnedLate, long stillOverdue) {}

  /** Current case counts by status, plus cases reported and completed in the period. */
  public record MaintenanceSummary(
      Map<String, Long> casesByStatus,
      long reportedInPeriod,
      long completedInPeriod,
      Duration averageCompletion) {}

  /** Cases reported in the period for one item, most first. */
  public record EquipmentMaintenance(
      UUID equipmentId, String assetTag, String name, String category, long cases) {}
}
