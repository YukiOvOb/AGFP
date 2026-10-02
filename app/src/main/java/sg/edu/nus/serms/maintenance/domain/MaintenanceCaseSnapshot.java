package sg.edu.nus.serms.maintenance.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable durable state aligned with the merged V002 maintenance_case model. */
public record MaintenanceCaseSnapshot(
    UUID maintenanceCaseId,
    UUID equipmentId,
    UUID reportedBy,
    UUID assignedTo,
    UUID loanId,
    MaintenanceStatus status,
    String faultDescription,
    String resolutionNote,
    Instant reportedAt,
    Instant resolvedAt,
    int version) {

  public MaintenanceCaseSnapshot {
    Objects.requireNonNull(maintenanceCaseId, "maintenanceCaseId");
    Objects.requireNonNull(equipmentId, "equipmentId");
    Objects.requireNonNull(reportedBy, "reportedBy");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(reportedAt, "reportedAt");
    faultDescription = requiredText(faultDescription, "faultDescription");
    require(version >= 0, "Negative version");
    boolean terminal = status == MaintenanceStatus.RESOLVED || status == MaintenanceStatus.UNREPAIRABLE;
    require(status == MaintenanceStatus.OPEN || assignedTo != null, "Assigned maintainer required");
    if (terminal) {
      resolutionNote = requiredText(resolutionNote, "resolutionNote");
      require(resolvedAt != null && !resolvedAt.isBefore(reportedAt), "Invalid resolution time");
    } else {
      require(resolvedAt == null && resolutionNote == null, "Resolution data before terminal status");
    }
  }

  private static String requiredText(String value, String field) {
    require(value != null && !value.isBlank(), field + " must not be blank");
    return value.strip();
  }

  private static void require(boolean valid, String message) {
    if (!valid) throw new IllegalArgumentException(message);
  }
}
