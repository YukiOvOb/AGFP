package sg.edu.nus.serms.maintenance.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import sg.edu.nus.serms.maintenance.service.security.MaintenanceAccessPolicy;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.serms.maintenance.domain.MaintenanceAssigned;
import sg.edu.nus.serms.maintenance.domain.MaintenanceCase;
import sg.edu.nus.serms.maintenance.domain.MaintenanceCompleted;
import sg.edu.nus.serms.maintenance.domain.MaintenanceHistoryAction;
import sg.edu.nus.serms.maintenance.domain.MaintenanceHistoryEntry;
import sg.edu.nus.serms.maintenance.domain.MaintenanceStatus;
import sg.edu.nus.serms.maintenance.repository.MaintenanceCaseRepository;
import sg.edu.nus.serms.maintenance.repository.MaintenanceHistoryRepository;

/** V002 orchestration across equipment, case, audit and synchronous event publication. */
@Service
@Transactional
public class MaintenanceService {
  private final MaintenanceCaseRepository cases;
  private final MaintenanceHistoryRepository history;
  private final EquipmentMaintenancePort equipment;
  private final TechnicianDirectoryPort technicians;
  private final MaintenanceEventPublisher events;
  private final Supplier<UUID> identifiers;
  private final Clock clock;

  public MaintenanceService(MaintenanceCaseRepository cases, MaintenanceHistoryRepository history,
      EquipmentMaintenancePort equipment, TechnicianDirectoryPort technicians,
      MaintenanceEventPublisher events,
      @Qualifier("maintenanceIdentifiers") Supplier<UUID> identifiers,
      @Qualifier("maintenanceClock") Clock clock) {
    this.cases = Objects.requireNonNull(cases, "cases");
    this.history = Objects.requireNonNull(history, "history");
    this.equipment = Objects.requireNonNull(equipment, "equipment");
    this.technicians = Objects.requireNonNull(technicians, "technicians");
    this.events = Objects.requireNonNull(events, "events");
    this.identifiers = Objects.requireNonNull(identifiers, "identifiers");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @PreAuthorize(MaintenanceAccessPolicy.REPORTER)
  public MaintenanceCase reportFault(UUID equipmentId, UUID reportedBy, String faultDescription) {
    return reportFault(equipmentId, reportedBy, faultDescription, null);
  }

  @PreAuthorize(MaintenanceAccessPolicy.REPORTER)
  public MaintenanceCase reportFault(UUID equipmentId, UUID reportedBy, String faultDescription,
      UUID loanId) {
    Instant now = clock.instant();
    var item = new MaintenanceCase(identifiers.get(), equipmentId, reportedBy, loanId,
        faultDescription, now);
    equipment.ensureEquipmentExists(equipmentId);
    equipment.lockForMaintenance(equipmentId);
    equipment.markUnderMaintenance(equipmentId);
    item = cases.insert(item);
    record(item, MaintenanceHistoryAction.REPORT_FAULT, null, reportedBy, now, item.faultDescription());
    return item.copy();
  }

  @PreAuthorize(MaintenanceAccessPolicy.ASSIGNER)
  public MaintenanceCase assignTechnician(UUID caseId, UUID technicianId, UUID actorId) {
    Objects.requireNonNull(actorId, "actorId");
    Objects.requireNonNull(technicianId, "technicianId");
    var item = load(caseId);
    technicians.ensureTechnician(technicianId);
    equipment.lockForMaintenance(item.equipmentId());
    var before = item.status();
    item.assign(technicianId);
    Instant now = clock.instant();
    item = cases.update(item);
    record(item, MaintenanceHistoryAction.ASSIGN, before, actorId, now, null);
    events.publish(new MaintenanceAssigned(caseId, item.equipmentId(), technicianId, actorId, now));
    return item.copy();
  }

  @PreAuthorize(MaintenanceAccessPolicy.TECHNICIAN_START)
  public MaintenanceCase startMaintenance(UUID caseId, UUID actorId) {
    Objects.requireNonNull(actorId, "actorId");
    var item = load(caseId);
    equipment.lockForMaintenance(item.equipmentId());
    item = load(caseId);
    requireAssignedTechnician(item, actorId);
    var before = item.status();
    item.start();
    item = cases.update(item);
    record(item, MaintenanceHistoryAction.START, before, actorId, clock.instant(), null);
    return item.copy();
  }

  @PreAuthorize(MaintenanceAccessPolicy.TECHNICIAN_WORK)
  public MaintenanceCase recordDiagnosis(UUID caseId, String diagnosis, UUID actorId) {
    return recordWork(caseId, diagnosis, actorId, MaintenanceHistoryAction.RECORD_DIAGNOSIS);
  }

  @PreAuthorize(MaintenanceAccessPolicy.TECHNICIAN_WORK)
  public MaintenanceCase recordRepairAction(UUID caseId, String repairAction, UUID actorId) {
    return recordWork(caseId, repairAction, actorId, MaintenanceHistoryAction.RECORD_REPAIR_ACTION);
  }

  @PreAuthorize(MaintenanceAccessPolicy.TECHNICIAN_WORK)
  public MaintenanceCase addMaintenanceNote(UUID caseId, String note, UUID actorId) {
    return recordWork(caseId, note, actorId, MaintenanceHistoryAction.ADD_NOTE);
  }

  private MaintenanceCase recordWork(UUID caseId, String detail, UUID actorId,
      MaintenanceHistoryAction action) {
    Objects.requireNonNull(actorId, "actorId");
    var item = load(caseId);
    if (detail == null || detail.isBlank()) {
      throw new IllegalArgumentException("Maintenance detail must not be blank");
    }
    // Serialize notes with completion as well; reload after the equipment lock to reject
    // a case that became terminal while this transaction was waiting for that lock.
    equipment.lockForMaintenance(item.equipmentId());
    item = load(caseId);
    requireAssignedTechnician(item, actorId);
    if (item.status() != MaintenanceStatus.IN_PROGRESS) {
      throw new MaintenanceStateConflictException("Maintenance work requires IN_PROGRESS status");
    }
    record(item, action, item.status(), actorId, clock.instant(), detail.strip());
    return item.copy();
  }

  @PreAuthorize(MaintenanceAccessPolicy.TECHNICIAN_WORK)
  public MaintenanceCase resolve(UUID caseId, String resolutionNote, UUID actorId) {
    return complete(caseId, resolutionNote, actorId, false);
  }

  @PreAuthorize(MaintenanceAccessPolicy.TECHNICIAN_WORK)
  public MaintenanceCase markUnrepairable(UUID caseId, String resolutionNote, UUID actorId) {
    return complete(caseId, resolutionNote, actorId, true);
  }

  private MaintenanceCase complete(UUID caseId, String resolutionNote, UUID actorId,
      boolean unrepairable) {
    Objects.requireNonNull(actorId, "actorId");
    var item = load(caseId);
    equipment.lockForMaintenance(item.equipmentId());
    item = load(caseId);
    requireAssignedTechnician(item, actorId);
    var before = item.status();
    Instant now = clock.instant();
    if (unrepairable) item.markUnrepairable(resolutionNote, now);
    else item.resolve(resolutionNote, now);
    // Recompute must see this case's persisted terminal state.
    item = cases.update(item);
    if (unrepairable) equipment.markRetired(item.equipmentId());
    else equipment.recomputeAfterResolvedMaintenance(item.equipmentId());
    record(item, unrepairable ? MaintenanceHistoryAction.MARK_UNREPAIRABLE
        : MaintenanceHistoryAction.RESOLVE, before, actorId, now, item.resolutionNote());
    events.publish(new MaintenanceCompleted(caseId, item.equipmentId(), item.assignedTo(), actorId,
        item.status(), now));
    return item.copy();
  }

  @Transactional(readOnly = true)
  @PreAuthorize(MaintenanceAccessPolicy.CASE_READER)
  public MaintenanceCase getCase(UUID caseId) { return load(caseId); }

  @Transactional(readOnly = true)
  @PreAuthorize(MaintenanceAccessPolicy.OPERATORS)
  public List<MaintenanceCase> getOpenCases() {
    return copies(cases.findOpenCases());
  }

  @Transactional(readOnly = true)
  @PreAuthorize(MaintenanceAccessPolicy.OPERATORS)
  public List<MaintenanceTechnician> getActiveTechnicians() {
    return List.copyOf(technicians.findActiveTechnicians());
  }

  @Transactional(readOnly = true)
  @PreAuthorize(MaintenanceAccessPolicy.OWN_CASES)
  public List<MaintenanceCase> getAssignedCases(UUID technicianId) {
    return copies(cases.findByAssignedTo(Objects.requireNonNull(technicianId, "technicianId")));
  }

  @Transactional(readOnly = true)
  @PreAuthorize(MaintenanceAccessPolicy.OPERATORS)
  public List<MaintenanceCase> getEquipmentCases(UUID equipmentId) {
    return copies(cases.findByEquipmentId(Objects.requireNonNull(equipmentId, "equipmentId")));
  }

  private static List<MaintenanceCase> copies(List<MaintenanceCase> items) {
    return items.stream().map(MaintenanceCase::copy).toList();
  }

  @Transactional(readOnly = true)
  @PreAuthorize(MaintenanceAccessPolicy.OPERATORS)
  public List<MaintenanceHistoryEntry> getEquipmentMaintenanceHistory(UUID equipmentId) {
    return List.copyOf(history.findByEquipmentId(Objects.requireNonNull(equipmentId)));
  }

  @Transactional(readOnly = true)
  @PreAuthorize(MaintenanceAccessPolicy.CASE_READER)
  public List<MaintenanceHistoryEntry> getCaseHistory(UUID caseId) {
    load(caseId);
    return List.copyOf(history.findByCaseId(Objects.requireNonNull(caseId)));
  }

  private static void requireAssignedTechnician(MaintenanceCase item, UUID actorId) {
    if (item.assignedTo() == null || !item.assignedTo().equals(actorId)) {
      throw new AccessDeniedException("Only the assigned technician may perform maintenance work");
    }
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
