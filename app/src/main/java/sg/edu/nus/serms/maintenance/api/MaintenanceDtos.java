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
      @NotBlank String faultDescription,
      @Schema(nullable = true, description = "Defaults to OTHER") FaultCategory faultCategory,
      @Schema(nullable = true, description = "Defaults to MEDIUM") MaintenancePriority priority) {}

  public record AssignTechnicianRequest(@NotNull UUID technicianId) {}
  @Schema(requiredProperties = {"userId", "email", "displayName"})
  public record MaintenanceTechnicianResponse(UUID userId, String email, String displayName) {
    public static MaintenanceTechnicianResponse from(
        sg.edu.nus.serms.maintenance.service.MaintenanceTechnician item) {
      return new MaintenanceTechnicianResponse(item.userId(), item.email(), item.displayName());
    }
  }
  public record MaintenanceDetailRequest(@NotBlank String detail) {}
  public record CompleteMaintenanceRequest(@NotBlank String resolutionNote) {}

  @Schema(requiredProperties = {"maintenanceCaseId", "equipmentId", "equipmentName",
      "equipmentAssetTag", "reportedBy", "reportedByName", "assignedTo", "assignedToName",
      "loanId", "status", "faultDescription", "faultCategory", "priority", "resolutionNote",
      "reportedAt", "resolvedAt", "version"})
  public record MaintenanceCaseResponse(
      UUID maintenanceCaseId, UUID equipmentId,
      @Schema(nullable = true, description = "Null only when the equipment record cannot be read")
      String equipmentName,
      @Schema(nullable = true, description = "Null only when the equipment record cannot be read")
      String equipmentAssetTag,
      UUID reportedBy,
      @Schema(nullable = true, description = "Reporter display name; null only when the user cannot be read")
      String reportedByName,
      @Schema(nullable = true) UUID assignedTo,
      @Schema(nullable = true, description = "Technician display name; null when unassigned")
      String assignedToName,
      @Schema(nullable = true) UUID loanId,
      MaintenanceStatus status, String faultDescription,
      FaultCategory faultCategory, MaintenancePriority priority,
      @Schema(nullable = true) String resolutionNote,
      Instant reportedAt,
      @Schema(nullable = true) Instant resolvedAt,
      @Schema(description = "Server-side version; mutations do not support If-Match or expectedVersion")
      long version) {
    public static MaintenanceCaseResponse from(MaintenanceCase item, MaintenanceNames names) {
      var equipment = names.equipment(item.equipmentId());
      return new MaintenanceCaseResponse(item.maintenanceCaseId(), item.equipmentId(),
          equipment == null ? null : equipment.name(),
          equipment == null ? null : equipment.assetTag(),
          item.reportedBy(), names.user(item.reportedBy()),
          item.assignedTo(), names.user(item.assignedTo()),
          item.loanId(), item.status(), item.faultDescription(),
          item.faultCategory(), item.priority(),
          item.resolutionNote(), item.reportedAt(), item.resolvedAt(), item.version());
    }
  }

  @Schema(requiredProperties = {"maintenanceCaseId", "equipmentId", "action", "fromStatus",
      "toStatus", "actorId", "actorName", "occurredAt", "detail"})
  public record MaintenanceHistoryResponse(
      UUID maintenanceCaseId, UUID equipmentId, MaintenanceHistoryAction action,
      @Schema(nullable = true) MaintenanceStatus fromStatus,
      MaintenanceStatus toStatus, UUID actorId,
      @Schema(nullable = true, description = "Actor display name; null only when the user cannot be read")
      String actorName,
      Instant occurredAt,
      @Schema(nullable = true) String detail) {
    public static MaintenanceHistoryResponse from(MaintenanceHistoryEntry item, MaintenanceNames names) {
      return new MaintenanceHistoryResponse(item.maintenanceCaseId(), item.equipmentId(),
          item.action(), item.fromStatus(), item.toStatus(), item.actorId(),
          names.user(item.actorId()), item.occurredAt(), item.detail());
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
