package sg.edu.nus.serms.maintenance.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable, persistence-neutral aggregate state; only reachable lifecycle shapes are accepted. */
public record MaintenanceCaseSnapshot(
    UUID maintenanceCaseId,
    UUID faultReportId,
    UUID equipmentId,
    MaintenanceStatus status,
    MaintenanceStatus completionOutcome,
    UUID assignedTechnicianId,
    Instant createdAt,
    Instant assignedAt,
    Instant startedAt,
    Instant completedAt,
    Instant closedAt,
    String diagnosis,
    String repairAction,
    List<String> maintenanceNotes,
    String completionInformation) {

  public MaintenanceCaseSnapshot {
    Objects.requireNonNull(maintenanceCaseId, "maintenanceCaseId");
    Objects.requireNonNull(faultReportId, "faultReportId");
    Objects.requireNonNull(equipmentId, "equipmentId");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(createdAt, "createdAt");
    maintenanceNotes = List.copyOf(maintenanceNotes);

    boolean assigned = status != MaintenanceStatus.REPORTED;
    boolean started = assigned && status != MaintenanceStatus.ASSIGNED;
    boolean completed = status == MaintenanceStatus.RESOLVED
        || status == MaintenanceStatus.NOT_REPAIRABLE || status == MaintenanceStatus.CLOSED;
    require((assignedTechnicianId != null) == assigned, "Technician does not match lifecycle");
    require((assignedAt != null) == assigned, "Assignment time does not match lifecycle");
    require((startedAt != null) == started, "Start time does not match lifecycle");
    require((completedAt != null) == completed, "Completion time does not match lifecycle");
    require((closedAt != null) == (status == MaintenanceStatus.CLOSED),
        "Close time does not match lifecycle");
    if (completed) {
      require(completionOutcome == MaintenanceStatus.RESOLVED
          || completionOutcome == MaintenanceStatus.NOT_REPAIRABLE, "Missing completion outcome");
      require(status == MaintenanceStatus.CLOSED || completionOutcome == status,
          "Outcome does not match status");
      require(completionInformation != null && !completionInformation.isBlank(),
          "Missing completion information");
    } else {
      require(completionOutcome == null && completionInformation == null,
          "Completion data before completion");
    }
    require(started || (diagnosis == null && repairAction == null && maintenanceNotes.isEmpty()),
        "Work information before start");
    require(diagnosis == null || !diagnosis.isBlank(), "Blank diagnosis");
    require(repairAction == null || !repairAction.isBlank(), "Blank repair action");
    maintenanceNotes.forEach(note -> require(!note.isBlank(), "Blank maintenance note"));

    Instant previous = createdAt;
    for (Instant timestamp : new Instant[] {assignedAt, startedAt, completedAt, closedAt}) {
      if (timestamp != null) {
        require(!timestamp.isBefore(previous), "Lifecycle timestamps out of order");
        previous = timestamp;
      }
    }
  }

  private static void require(boolean valid, String message) {
    if (!valid) throw new IllegalArgumentException(message);
  }
}
