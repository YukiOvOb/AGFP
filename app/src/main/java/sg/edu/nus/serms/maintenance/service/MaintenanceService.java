package sg.edu.nus.serms.maintenance.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import sg.edu.nus.serms.maintenance.domain.FaultReport;
import sg.edu.nus.serms.maintenance.domain.MaintenanceAssigned;
import sg.edu.nus.serms.maintenance.domain.MaintenanceCase;
import sg.edu.nus.serms.maintenance.domain.MaintenanceCompleted;
import sg.edu.nus.serms.maintenance.domain.MaintenanceHistoryAction;
import sg.edu.nus.serms.maintenance.domain.MaintenanceHistoryEntry;
import sg.edu.nus.serms.maintenance.domain.MaintenanceStatus;
import sg.edu.nus.serms.maintenance.repository.FaultReportRepository;
import sg.edu.nus.serms.maintenance.repository.MaintenanceCaseRepository;
import sg.edu.nus.serms.maintenance.repository.MaintenanceHistoryRepository;

/** UC04 orchestration. Future integration must wrap each mutation in one shared transaction. */
public final class MaintenanceService {
  private final FaultReportRepository faultReports;
  private final MaintenanceCaseRepository cases;
  private final MaintenanceHistoryRepository history;
  private final EquipmentMaintenancePort equipment;
  private final TechnicianDirectoryPort technicians;
  private final MaintenanceEventPublisher events;
  private final Supplier<UUID> identifiers;
  private final Clock clock;

  public MaintenanceService(
      FaultReportRepository faultReports,
      MaintenanceCaseRepository cases,
      MaintenanceHistoryRepository history,
      EquipmentMaintenancePort equipment,
      TechnicianDirectoryPort technicians,
      MaintenanceEventPublisher events,
      Supplier<UUID> identifiers,
      Clock clock) {
    this.faultReports = Objects.requireNonNull(faultReports, "faultReports");
    this.cases = Objects.requireNonNull(cases, "cases");
    this.history = Objects.requireNonNull(history, "history");
    this.equipment = Objects.requireNonNull(equipment, "equipment");
    this.technicians = Objects.requireNonNull(technicians, "technicians");
    this.events = Objects.requireNonNull(events, "events");
    this.identifiers = Objects.requireNonNull(identifiers, "identifiers");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public MaintenanceCase reportFault(
      UUID equipmentId,
      String faultCategory,
      String description,
      UUID reporterId,
      String priority) {
    Instant now = clock.instant();
    FaultReport report =
        new FaultReport(
            identifiers.get(), equipmentId, faultCategory, description, reporterId, now, priority);
    MaintenanceCase maintenanceCase =
        new MaintenanceCase(identifiers.get(), report.faultReportId(), equipmentId, now);

    equipment.ensureEquipmentExists(equipmentId);
    equipment.markUnderMaintenance(equipmentId);
    faultReports.save(report);
    cases.save(maintenanceCase);
    history.append(
        entry(
            maintenanceCase,
            MaintenanceHistoryAction.REPORT_FAULT,
            null,
            reporterId,
            now,
            report.description()));
    return maintenanceCase.copy();
  }

  public MaintenanceCase assignTechnician(UUID caseId, UUID technicianId, UUID actorId) {
    Objects.requireNonNull(technicianId, "technicianId");
    Objects.requireNonNull(actorId, "actorId");
    technicians.ensureTechnician(technicianId);
    MaintenanceCase maintenanceCase = loadForUpdate(caseId);
    Instant now = clock.instant();
    MaintenanceStatus before = maintenanceCase.status();
    maintenanceCase.assign(technicianId, now);
    saveAndRecord(maintenanceCase, MaintenanceHistoryAction.ASSIGN, before, actorId, now, null);
    events.publish(
        new MaintenanceAssigned(caseId, maintenanceCase.equipmentId(), technicianId, actorId, now));
    return maintenanceCase.copy();
  }

  public MaintenanceCase startMaintenance(UUID caseId, UUID actorId) {
    Objects.requireNonNull(actorId, "actorId");
    MaintenanceCase maintenanceCase = loadForUpdate(caseId);
    Instant now = clock.instant();
    MaintenanceStatus before = maintenanceCase.status();
    maintenanceCase.start(now);
    saveAndRecord(maintenanceCase, MaintenanceHistoryAction.START, before, actorId, now, null);
    return maintenanceCase.copy();
  }

  public MaintenanceCase recordDiagnosis(UUID caseId, String diagnosis, UUID actorId) {
    Objects.requireNonNull(actorId, "actorId");
    MaintenanceCase maintenanceCase = loadForUpdate(caseId);
    maintenanceCase.recordDiagnosis(diagnosis);
    Instant now = clock.instant();
    saveAndRecord(
        maintenanceCase,
        MaintenanceHistoryAction.RECORD_DIAGNOSIS,
        maintenanceCase.status(),
        actorId,
        now,
        maintenanceCase.diagnosis());
    return maintenanceCase.copy();
  }

  public MaintenanceCase recordRepairAction(UUID caseId, String repairAction, UUID actorId) {
    Objects.requireNonNull(actorId, "actorId");
    MaintenanceCase maintenanceCase = loadForUpdate(caseId);
    maintenanceCase.recordRepairAction(repairAction);
    Instant now = clock.instant();
    saveAndRecord(
        maintenanceCase,
        MaintenanceHistoryAction.RECORD_REPAIR_ACTION,
        maintenanceCase.status(),
        actorId,
        now,
        maintenanceCase.repairAction());
    return maintenanceCase.copy();
  }

  public MaintenanceCase addMaintenanceNote(UUID caseId, String note, UUID actorId) {
    Objects.requireNonNull(actorId, "actorId");
    MaintenanceCase maintenanceCase = loadForUpdate(caseId);
    maintenanceCase.recordMaintenanceNotes(note);
    Instant now = clock.instant();
    saveAndRecord(
        maintenanceCase,
        MaintenanceHistoryAction.ADD_NOTE,
        maintenanceCase.status(),
        actorId,
        now,
        maintenanceCase.maintenanceNotes().get(maintenanceCase.maintenanceNotes().size() - 1));
    return maintenanceCase.copy();
  }

  public MaintenanceCase resolve(UUID caseId, String completionInformation, UUID actorId) {
    Objects.requireNonNull(actorId, "actorId");
    MaintenanceCase maintenanceCase = loadForUpdate(caseId);
    Instant now = clock.instant();
    MaintenanceStatus before = maintenanceCase.status();
    maintenanceCase.resolve(completionInformation, now);
    saveAndRecord(
        maintenanceCase,
        MaintenanceHistoryAction.RESOLVE,
        before,
        actorId,
        now,
        maintenanceCase.completionInformation());
    return maintenanceCase.copy();
  }

  public MaintenanceCase markNotRepairable(
      UUID caseId, String completionInformation, UUID actorId) {
    Objects.requireNonNull(actorId, "actorId");
    MaintenanceCase maintenanceCase = loadForUpdate(caseId);
    Instant now = clock.instant();
    MaintenanceStatus before = maintenanceCase.status();
    maintenanceCase.markNotRepairable(completionInformation, now);
    saveAndRecord(
        maintenanceCase,
        MaintenanceHistoryAction.MARK_NOT_REPAIRABLE,
        before,
        actorId,
        now,
        maintenanceCase.completionInformation());
    return maintenanceCase.copy();
  }

  public MaintenanceCase close(UUID caseId, UUID actorId) {
    Objects.requireNonNull(actorId, "actorId");
    MaintenanceCase maintenanceCase = loadForUpdate(caseId);
    Instant now = clock.instant();
    MaintenanceStatus outcome = maintenanceCase.status();
    maintenanceCase.close(now);
    switch (outcome) {
      case RESOLVED -> equipment.markAvailable(maintenanceCase.equipmentId());
      case NOT_REPAIRABLE -> equipment.markRetired(maintenanceCase.equipmentId());
      default -> throw new IllegalStateException("Unexpected maintenance completion outcome");
    }
    saveAndRecord(maintenanceCase, MaintenanceHistoryAction.CLOSE, outcome, actorId, now, null);
    events.publish(
        new MaintenanceCompleted(
            caseId,
            maintenanceCase.equipmentId(),
            maintenanceCase.assignedTechnicianId(),
            actorId,
            outcome,
            now));
    return maintenanceCase.copy();
  }

  public MaintenanceCase getCase(UUID caseId) {
    return loadForUpdate(caseId);
  }

  public List<MaintenanceHistoryEntry> getEquipmentMaintenanceHistory(UUID equipmentId) {
    return List.copyOf(history.findByEquipmentId(Objects.requireNonNull(equipmentId)));
  }

  public List<MaintenanceHistoryEntry> getCaseHistory(UUID caseId) {
    return List.copyOf(history.findByCaseId(Objects.requireNonNull(caseId)));
  }

  private MaintenanceCase loadForUpdate(UUID caseId) {
    Objects.requireNonNull(caseId, "caseId");
    return cases.findById(caseId)
        .orElseThrow(() -> new MaintenanceCaseNotFoundException(caseId))
        .copy();
  }

  private void saveAndRecord(
      MaintenanceCase maintenanceCase,
      MaintenanceHistoryAction action,
      MaintenanceStatus before,
      UUID actorId,
      Instant at,
      String detail) {
    Objects.requireNonNull(actorId, "actorId");
    cases.save(maintenanceCase);
    history.append(entry(maintenanceCase, action, before, actorId, at, detail));
  }

  private static MaintenanceHistoryEntry entry(
      MaintenanceCase maintenanceCase,
      MaintenanceHistoryAction action,
      MaintenanceStatus before,
      UUID actorId,
      Instant at,
      String detail) {
    return new MaintenanceHistoryEntry(
        maintenanceCase.maintenanceCaseId(),
        maintenanceCase.equipmentId(),
        action,
        before,
        maintenanceCase.status(),
        actorId,
        at,
        detail);
  }
}
