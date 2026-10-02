package sg.edu.nus.serms.maintenance.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import sg.edu.nus.serms.maintenance.domain.MaintenanceAssigned;
import sg.edu.nus.serms.maintenance.domain.MaintenanceCase;
import sg.edu.nus.serms.maintenance.domain.MaintenanceCompleted;
import sg.edu.nus.serms.maintenance.domain.MaintenanceHistoryAction;
import sg.edu.nus.serms.maintenance.domain.MaintenanceHistoryEntry;
import sg.edu.nus.serms.maintenance.domain.MaintenanceStatus;
import sg.edu.nus.serms.maintenance.repository.MaintenanceCaseRepository;
import sg.edu.nus.serms.maintenance.repository.MaintenanceHistoryRepository;

/** V002 orchestration; integration must wrap each mutation in one shared transaction. */
public final class MaintenanceService {
  private final MaintenanceCaseRepository cases;
  private final MaintenanceHistoryRepository history;
  private final EquipmentMaintenancePort equipment;
  private final TechnicianDirectoryPort technicians;
  private final MaintenanceEventPublisher events;
  private final Supplier<UUID> identifiers;
  private final Clock clock;

  public MaintenanceService(MaintenanceCaseRepository cases, MaintenanceHistoryRepository history,
      EquipmentMaintenancePort equipment, TechnicianDirectoryPort technicians,
      MaintenanceEventPublisher events, Supplier<UUID> identifiers, Clock clock) {
    this.cases = Objects.requireNonNull(cases, "cases");
    this.history = Objects.requireNonNull(history, "history");
    this.equipment = Objects.requireNonNull(equipment, "equipment");
    this.technicians = Objects.requireNonNull(technicians, "technicians");
    this.events = Objects.requireNonNull(events, "events");
    this.identifiers = Objects.requireNonNull(identifiers, "identifiers");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public MaintenanceCase reportFault(UUID equipmentId, UUID reportedBy, String faultDescription) {
    return reportFault(equipmentId, reportedBy, faultDescription, null);
  }

  public MaintenanceCase reportFault(UUID equipmentId, UUID reportedBy, String faultDescription,
      UUID loanId) {
    Instant now = clock.instant();
    var item = new MaintenanceCase(identifiers.get(), equipmentId, reportedBy, loanId,
        faultDescription, now);
    equipment.ensureEquipmentExists(equipmentId);
    equipment.markUnderMaintenance(equipmentId);
    cases.save(item);
    record(item, MaintenanceHistoryAction.REPORT_FAULT, null, reportedBy, now, item.faultDescription());
    return item.copy();
  }

  public MaintenanceCase assignTechnician(UUID caseId, UUID technicianId, UUID actorId) {
    Objects.requireNonNull(actorId, "actorId");
    Objects.requireNonNull(technicianId, "technicianId");
    var item = load(caseId);
    technicians.ensureTechnician(technicianId);
    var before = item.status();
    item.assign(technicianId);
    Instant now = clock.instant();
    cases.save(item);
    record(item, MaintenanceHistoryAction.ASSIGN, before, actorId, now, null);
    events.publish(new MaintenanceAssigned(caseId, item.equipmentId(), technicianId, actorId, now));
    return item.copy();
  }

  public MaintenanceCase startMaintenance(UUID caseId, UUID actorId) {
    Objects.requireNonNull(actorId, "actorId");
    var item = load(caseId);
    var before = item.status();
    item.start();
    cases.save(item);
    record(item, MaintenanceHistoryAction.START, before, actorId, clock.instant(), null);
    return item.copy();
  }

  public MaintenanceCase recordDiagnosis(UUID caseId, String diagnosis, UUID actorId) {
    return recordWork(caseId, diagnosis, actorId, MaintenanceHistoryAction.RECORD_DIAGNOSIS);
  }

  public MaintenanceCase recordRepairAction(UUID caseId, String repairAction, UUID actorId) {
    return recordWork(caseId, repairAction, actorId, MaintenanceHistoryAction.RECORD_REPAIR_ACTION);
  }

  public MaintenanceCase addMaintenanceNote(UUID caseId, String note, UUID actorId) {
    return recordWork(caseId, note, actorId, MaintenanceHistoryAction.ADD_NOTE);
  }

  private MaintenanceCase recordWork(UUID caseId, String detail, UUID actorId,
      MaintenanceHistoryAction action) {
    Objects.requireNonNull(actorId, "actorId");
    var item = load(caseId);
    if (item.status() != MaintenanceStatus.IN_PROGRESS) {
      throw new IllegalStateException("Maintenance work requires IN_PROGRESS status");
    }
    if (detail == null || detail.isBlank()) {
      throw new IllegalArgumentException("Maintenance detail must not be blank");
    }
    record(item, action, item.status(), actorId, clock.instant(), detail.strip());
    return item.copy();
  }

  public MaintenanceCase resolve(UUID caseId, String resolutionNote, UUID actorId) {
    return complete(caseId, resolutionNote, actorId, false);
  }

  public MaintenanceCase markUnrepairable(UUID caseId, String resolutionNote, UUID actorId) {
    return complete(caseId, resolutionNote, actorId, true);
  }

  private MaintenanceCase complete(UUID caseId, String resolutionNote, UUID actorId,
      boolean unrepairable) {
    Objects.requireNonNull(actorId, "actorId");
    var item = load(caseId);
    var before = item.status();
    Instant now = clock.instant();
    if (unrepairable) item.markUnrepairable(resolutionNote, now);
    else item.resolve(resolutionNote, now);
    // Recompute must see this case's terminal state, so save before invoking Equipment.
    cases.save(item);
    if (unrepairable) equipment.markRetired(item.equipmentId());
    else equipment.recomputeAfterResolvedMaintenance(item.equipmentId());
    record(item, unrepairable ? MaintenanceHistoryAction.MARK_UNREPAIRABLE
        : MaintenanceHistoryAction.RESOLVE, before, actorId, now, item.resolutionNote());
    events.publish(new MaintenanceCompleted(caseId, item.equipmentId(), item.assignedTo(), actorId,
        item.status(), now));
    return item.copy();
  }

  public MaintenanceCase getCase(UUID caseId) { return load(caseId); }

  public List<MaintenanceHistoryEntry> getEquipmentMaintenanceHistory(UUID equipmentId) {
    return List.copyOf(history.findByEquipmentId(Objects.requireNonNull(equipmentId)));
  }

  public List<MaintenanceHistoryEntry> getCaseHistory(UUID caseId) {
    return List.copyOf(history.findByCaseId(Objects.requireNonNull(caseId)));
  }

  private MaintenanceCase load(UUID caseId) {
    return cases.findById(Objects.requireNonNull(caseId))
        .orElseThrow(() -> new MaintenanceCaseNotFoundException(caseId)).copy();
  }

  private void record(MaintenanceCase item, MaintenanceHistoryAction action, MaintenanceStatus before,
      UUID actorId, Instant at, String detail) {
    history.append(new MaintenanceHistoryEntry(item.maintenanceCaseId(), item.equipmentId(), action,
        before, item.status(), actorId, at, detail));
  }
}
