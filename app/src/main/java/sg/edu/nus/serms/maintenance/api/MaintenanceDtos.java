package sg.edu.nus.serms.maintenance.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import sg.edu.nus.serms.maintenance.domain.*;
import sg.edu.nus.serms.maintenance.service.MaintenanceReportSnapshot;

public final class MaintenanceDtos {
  private MaintenanceDtos() {}

  public record ReportFaultRequest(
      @NotNull UUID equipmentId,
      @Schema(nullable = true) UUID loanId,
      @NotBlank String faultDescription) {}

  public record AssignTechnicianRequest(@NotNull UUID technicianId) {}
  public record MaintenanceDetailRequest(@NotBlank String detail) {}
  public record CompleteMaintenanceRequest(@NotBlank String resolutionNote) {}

  @Schema(requiredProperties = {"maintenanceCaseId", "equipmentId", "reportedBy", "assignedTo",
      "loanId", "status", "faultDescription", "resolutionNote", "reportedAt", "resolvedAt", "version"})
  public record MaintenanceCaseResponse(
      UUID maintenanceCaseId, UUID equipmentId, UUID reportedBy,
      @Schema(nullable = true) UUID assignedTo,
      @Schema(nullable = true) UUID loanId,
      MaintenanceStatus status, String faultDescription,
      @Schema(nullable = true) String resolutionNote,
      Instant reportedAt,
      @Schema(nullable = true) Instant resolvedAt,
      @Schema(description = "Server-side version; mutations do not support If-Match or expectedVersion")
      int version) {
    public static MaintenanceCaseResponse from(MaintenanceCase item) {
      return new MaintenanceCaseResponse(item.maintenanceCaseId(), item.equipmentId(),
          item.reportedBy(), item.assignedTo(), item.loanId(), item.status(), item.faultDescription(),
          item.resolutionNote(), item.reportedAt(), item.resolvedAt(), item.version());
    }
  }

  @Schema(requiredProperties = {"maintenanceCaseId", "equipmentId", "action", "fromStatus",
      "toStatus", "actorId", "occurredAt", "detail"})
  public record MaintenanceHistoryResponse(
      UUID maintenanceCaseId, UUID equipmentId, MaintenanceHistoryAction action,
      @Schema(nullable = true) MaintenanceStatus fromStatus,
      MaintenanceStatus toStatus, UUID actorId, Instant occurredAt,
      @Schema(nullable = true) String detail) {
    public static MaintenanceHistoryResponse from(MaintenanceHistoryEntry item) {
      return new MaintenanceHistoryResponse(item.maintenanceCaseId(), item.equipmentId(),
          item.action(), item.fromStatus(), item.toStatus(), item.actorId(), item.occurredAt(), item.detail());
    }
  }

  @Schema(requiredProperties = {"fromInclusive", "toExclusive", "casesByStatus", "reportedCaseCount",
      "completedCaseCount", "averageCompletionDuration"})
  public record MaintenanceReportResponse(
      Instant fromInclusive, Instant toExclusive, Map<MaintenanceStatus, Long> casesByStatus,
      long reportedCaseCount, long completedCaseCount,
      @Schema(type = "string", format = "duration", nullable = true,
          description = "ISO-8601 duration; null when the completion interval is empty")
      String averageCompletionDuration) {
    public MaintenanceReportResponse { casesByStatus = Map.copyOf(casesByStatus); }
    public static MaintenanceReportResponse from(MaintenanceReportSnapshot item) {
      return new MaintenanceReportResponse(item.fromInclusive(), item.toExclusive(),
          item.casesByStatus(), item.reportedCaseCount(), item.completedCaseCount(),
          item.averageCompletionDuration().map(Object::toString).orElse(null));
    }
  }
}
