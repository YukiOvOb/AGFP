package sg.edu.nus.serms.maintenance.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MaintenanceCompleted(
    UUID maintenanceCaseId,
    UUID equipmentId,
    UUID technicianId,
    UUID actorId,
    MaintenanceStatus outcome,
    Instant occurredAt) {
  public MaintenanceCompleted {
    Objects.requireNonNull(maintenanceCaseId, "maintenanceCaseId");
    Objects.requireNonNull(equipmentId, "equipmentId");
    Objects.requireNonNull(technicianId, "technicianId");
    Objects.requireNonNull(actorId, "actorId");
    Objects.requireNonNull(occurredAt, "occurredAt");
    if (outcome != MaintenanceStatus.RESOLVED && outcome != MaintenanceStatus.NOT_REPAIRABLE) {
      throw new IllegalArgumentException("Completion outcome must be RESOLVED or NOT_REPAIRABLE");
    }
  }
}
