package sg.edu.nus.serms.maintenance.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MaintenanceAssigned(
    UUID maintenanceCaseId,
    UUID equipmentId,
    UUID technicianId,
    UUID actorId,
    Instant occurredAt) {
  public MaintenanceAssigned {
    Objects.requireNonNull(maintenanceCaseId, "maintenanceCaseId");
    Objects.requireNonNull(equipmentId, "equipmentId");
    Objects.requireNonNull(technicianId, "technicianId");
    Objects.requireNonNull(actorId, "actorId");
    Objects.requireNonNull(occurredAt, "occurredAt");
  }
}
