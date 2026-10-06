package sg.edu.nus.serms.maintenance.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** UC04 aggregate containing only state representable by V002. */
public final class MaintenanceCase {
  private static final MaintenanceStateMachine TRANSITIONS = new MaintenanceStateMachine();
  private final UUID maintenanceCaseId;
  private final UUID equipmentId;
  private final UUID reportedBy;
  private final UUID loanId;
  private final String faultDescription;
  private final Instant reportedAt;
  private final int version;
  private MaintenanceStatus status;
  private UUID assignedTo;
  private String resolutionNote;
  private Instant resolvedAt;

  public MaintenanceCase(UUID maintenanceCaseId, UUID equipmentId, UUID reportedBy,
      UUID loanId, String faultDescription, Instant reportedAt) {
    this(new MaintenanceCaseSnapshot(maintenanceCaseId, equipmentId, reportedBy, null, loanId,
        MaintenanceStatus.OPEN, faultDescription, null, reportedAt, null, 0));
  }

  private MaintenanceCase(MaintenanceCaseSnapshot snapshot) {
    maintenanceCaseId = snapshot.maintenanceCaseId();
    equipmentId = snapshot.equipmentId();
    reportedBy = snapshot.reportedBy();
    assignedTo = snapshot.assignedTo();
    loanId = snapshot.loanId();
    status = snapshot.status();
    faultDescription = snapshot.faultDescription();
    resolutionNote = snapshot.resolutionNote();
    reportedAt = snapshot.reportedAt();
    resolvedAt = snapshot.resolvedAt();
    version = snapshot.version();
  }

  public static MaintenanceCase restore(MaintenanceCaseSnapshot snapshot) {
    return new MaintenanceCase(Objects.requireNonNull(snapshot, "snapshot"));
  }

  public MaintenanceCaseSnapshot snapshot() {
    return new MaintenanceCaseSnapshot(maintenanceCaseId, equipmentId, reportedBy, assignedTo,
        loanId, status, faultDescription, resolutionNote, reportedAt, resolvedAt, version);
  }

  public MaintenanceCase copy() { return restore(snapshot()); }

  public UUID maintenanceCaseId() { return maintenanceCaseId; }
  public UUID equipmentId() { return equipmentId; }
  public UUID reportedBy() { return reportedBy; }
  public UUID assignedTo() { return assignedTo; }
  public UUID loanId() { return loanId; }
  public MaintenanceStatus status() { return status; }
  public String faultDescription() { return faultDescription; }
  public String resolutionNote() { return resolutionNote; }
  public Instant reportedAt() { return reportedAt; }
  public Instant resolvedAt() { return resolvedAt; }
  /** Persisted version. Only the database trigger increments it; restoration loads the result. */
  public int version() { return version; }

  public void assign(UUID technicianId) {
    MaintenanceStatus next = TRANSITIONS.transition(status, MaintenanceAction.ASSIGN);
    assignedTo = Objects.requireNonNull(technicianId, "technicianId");
    status = next;
  }

  public void start() {
    status = TRANSITIONS.transition(status, MaintenanceAction.START);
  }

  public void resolve(String note, Instant at) { complete(MaintenanceAction.RESOLVE, note, at); }

  public void markUnrepairable(String note, Instant at) {
    complete(MaintenanceAction.MARK_UNREPAIRABLE, note, at);
  }

  private void complete(MaintenanceAction action, String note, Instant at) {
    MaintenanceStatus next = TRANSITIONS.transition(status, action);
    // Validate the complete proposed state before changing any aggregate field.
    var completed = new MaintenanceCaseSnapshot(maintenanceCaseId, equipmentId, reportedBy,
        assignedTo, loanId, next, faultDescription, note, reportedAt, at, version);
    resolutionNote = completed.resolutionNote();
    resolvedAt = completed.resolvedAt();
    status = next;
  }
}
