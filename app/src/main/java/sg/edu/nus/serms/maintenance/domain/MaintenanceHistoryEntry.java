package sg.edu.nus.serms.maintenance.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MaintenanceHistoryEntry(
    UUID maintenanceCaseId,
    UUID equipmentId,
    MaintenanceHistoryAction action,
    MaintenanceStatus fromStatus,
    MaintenanceStatus toStatus,
    UUID actorId,
    Instant occurredAt,
    String detail) {
  public MaintenanceHistoryEntry {
    Objects.requireNonNull(maintenanceCaseId, "maintenanceCaseId");
    Objects.requireNonNull(equipmentId, "equipmentId");
    Objects.requireNonNull(action, "action");
    Objects.requireNonNull(toStatus, "toStatus");
    Objects.requireNonNull(actorId, "actorId");
    Objects.requireNonNull(occurredAt, "occurredAt");
    if (fromStatus == null && action != MaintenanceHistoryAction.REPORT_FAULT) {
      throw new IllegalArgumentException("Only fault reporting may start without a previous status");
    }
  }
}
