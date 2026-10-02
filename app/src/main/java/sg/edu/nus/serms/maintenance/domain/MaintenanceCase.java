package sg.edu.nus.serms.maintenance.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class MaintenanceCase {
  private static final MaintenanceStateMachine TRANSITIONS = new MaintenanceStateMachine();

  private final UUID maintenanceCaseId;
  private final UUID faultReportId;
  private final UUID equipmentId;
  private final Instant createdAt;
  private MaintenanceStatus status = MaintenanceStatus.REPORTED;
  private MaintenanceStatus completionOutcome;
  private UUID assignedTechnicianId;
  private Instant assignedAt;
  private Instant startedAt;
  private Instant completedAt;
  private Instant closedAt;
  private String diagnosis;
  private String repairAction;
  private final List<String> maintenanceNotes = new ArrayList<>();
  private String completionInformation;

  public MaintenanceCase(
      UUID maintenanceCaseId, UUID faultReportId, UUID equipmentId, Instant createdAt) {
    this.maintenanceCaseId = Objects.requireNonNull(maintenanceCaseId, "maintenanceCaseId");
    this.faultReportId = Objects.requireNonNull(faultReportId, "faultReportId");
    this.equipmentId = Objects.requireNonNull(equipmentId, "equipmentId");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
  }

  private MaintenanceCase(MaintenanceCase source) {
    this(source.maintenanceCaseId, source.faultReportId, source.equipmentId, source.createdAt);
    status = source.status;
    completionOutcome = source.completionOutcome;
    assignedTechnicianId = source.assignedTechnicianId;
    assignedAt = source.assignedAt;
    startedAt = source.startedAt;
    completedAt = source.completedAt;
    closedAt = source.closedAt;
    diagnosis = source.diagnosis;
    repairAction = source.repairAction;
    maintenanceNotes.addAll(source.maintenanceNotes);
    completionInformation = source.completionInformation;
  }

  /** An independent snapshot, so an unsuccessful orchestration cannot mutate a loaded instance. */
  public MaintenanceCase copy() {
    return new MaintenanceCase(this);
  }

  public MaintenanceCaseSnapshot snapshot() {
    return new MaintenanceCaseSnapshot(
        maintenanceCaseId, faultReportId, equipmentId, status, completionOutcome,
        assignedTechnicianId, createdAt, assignedAt, startedAt, completedAt, closedAt,
        diagnosis, repairAction, maintenanceNotes, completionInformation);
  }

  public static MaintenanceCase restore(MaintenanceCaseSnapshot snapshot) {
    Objects.requireNonNull(snapshot, "snapshot");
    MaintenanceCase restored = new MaintenanceCase(
        snapshot.maintenanceCaseId(), snapshot.faultReportId(), snapshot.equipmentId(),
        snapshot.createdAt());
    restored.status = snapshot.status();
    restored.completionOutcome = snapshot.completionOutcome();
    restored.assignedTechnicianId = snapshot.assignedTechnicianId();
    restored.assignedAt = snapshot.assignedAt();
    restored.startedAt = snapshot.startedAt();
    restored.completedAt = snapshot.completedAt();
    restored.closedAt = snapshot.closedAt();
    restored.diagnosis = snapshot.diagnosis();
    restored.repairAction = snapshot.repairAction();
    restored.maintenanceNotes.addAll(snapshot.maintenanceNotes());
    restored.completionInformation = snapshot.completionInformation();
    return restored;
  }

  public MaintenanceStatus completionOutcome() {
    return completionOutcome;
  }

  public UUID maintenanceCaseId() {
    return maintenanceCaseId;
  }

  public UUID faultReportId() {
    return faultReportId;
  }

  public UUID equipmentId() {
    return equipmentId;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public MaintenanceStatus status() {
    return status;
  }

  public UUID assignedTechnicianId() {
    return assignedTechnicianId;
  }

  public Instant assignedAt() {
    return assignedAt;
  }

  public Instant startedAt() {
    return startedAt;
  }

  public Instant completedAt() {
    return completedAt;
  }

  public Instant closedAt() {
    return closedAt;
  }

  public String diagnosis() {
    return diagnosis;
  }

  public String repairAction() {
    return repairAction;
  }

  public List<String> maintenanceNotes() {
    return List.copyOf(maintenanceNotes);
  }

  public String completionInformation() {
    return completionInformation;
  }

  public void assign(UUID technicianId, Instant at) {
    MaintenanceStatus next = TRANSITIONS.transition(status, MaintenanceAction.ASSIGN);
    Objects.requireNonNull(technicianId, "technicianId");
    requireNotBefore(at, createdAt);
    assignedTechnicianId = technicianId;
    assignedAt = at;
    status = next;
  }

  public void start(Instant at) {
    MaintenanceStatus next = TRANSITIONS.transition(status, MaintenanceAction.START);
    requireNotBefore(at, assignedAt);
    startedAt = at;
    status = next;
  }

  public void recordDiagnosis(String diagnosis) {
    requireWorkInProgress();
    this.diagnosis = requireText(diagnosis);
  }

  public void recordRepairAction(String repairAction) {
    requireWorkInProgress();
    this.repairAction = requireText(repairAction);
  }

  public void recordMaintenanceNotes(String note) {
    requireWorkInProgress();
    maintenanceNotes.add(requireText(note));
  }

  public void resolve(String information, Instant at) {
    complete(MaintenanceAction.RESOLVE, information, at);
  }

  public void markNotRepairable(String information, Instant at) {
    complete(MaintenanceAction.MARK_NOT_REPAIRABLE, information, at);
  }

  public void close(Instant at) {
    MaintenanceStatus next = TRANSITIONS.transition(status, MaintenanceAction.CLOSE);
    requireNotBefore(at, completedAt);
    closedAt = at;
    status = next;
  }

  private void complete(MaintenanceAction action, String information, Instant at) {
    MaintenanceStatus next = TRANSITIONS.transition(status, action);
    String text = requireText(information);
    requireNotBefore(at, startedAt);
    completionInformation = text;
    completedAt = at;
    completionOutcome = next;
    status = next;
  }

  private void requireWorkInProgress() {
    if (status != MaintenanceStatus.IN_PROGRESS) {
      throw new IllegalStateException("Maintenance work requires IN_PROGRESS status; current: " + status);
    }
  }

  private static void requireNotBefore(Instant at, Instant previous) {
    Objects.requireNonNull(at, "transition time");
    if (at.isBefore(previous)) {
      throw new IllegalArgumentException("Transition time precedes the previous lifecycle step");
    }
  }

  private static String requireText(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Maintenance information must not be blank");
    }
    return value.strip();
  }
}
