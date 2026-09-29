package sg.edu.nus.serms.maintenance.service;

import static org.assertj.core.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import sg.edu.nus.serms.maintenance.domain.*;
import sg.edu.nus.serms.maintenance.repository.*;
import org.junit.jupiter.api.Test;

class MaintenanceServiceTest {
  private static final UUID EQUIPMENT_ID = new UUID(0, 1);
  private static final UUID REPORTER_ID = new UUID(0, 2);
  private static final UUID TECHNICIAN_ID = new UUID(0, 3);
  private static final UUID ACTOR_ID = new UUID(0, 4);
  private static final UUID REPORT_ID = new UUID(0, 5);
  private static final UUID CASE_ID = new UUID(0, 6);
  private static final Instant NOW = Instant.parse("2026-09-29T08:00:00Z");

  private final FakeFaultReports faultReports = new FakeFaultReports();
  private final FakeCases cases = new FakeCases();
  private final FakeHistory history = new FakeHistory();
  private final FakeEquipment equipment = new FakeEquipment();
  private final FakeTechnicians technicians = new FakeTechnicians();
  private final FakeEvents events = new FakeEvents();
  private final Queue<UUID> ids = new ArrayDeque<>(List.of(REPORT_ID, CASE_ID));
  private final MaintenanceService service =
      new MaintenanceService(
          faultReports,
          cases,
          history,
          equipment,
          technicians,
          events,
          ids::remove,
          Clock.fixed(NOW, ZoneOffset.UTC));

  private MaintenanceCase report() {
    return service.reportFault(EQUIPMENT_ID, "  electrical  ", "  Does not start  ",
        REPORTER_ID, "  urgent  ");
  }

  private MaintenanceCase assigned() {
    MaintenanceCase maintenanceCase = report();
    return service.assignTechnician(maintenanceCase.maintenanceCaseId(), TECHNICIAN_ID, ACTOR_ID);
  }

  private MaintenanceCase started() {
    MaintenanceCase maintenanceCase = assigned();
    return service.startMaintenance(maintenanceCase.maintenanceCaseId(), TECHNICIAN_ID);
  }

  @Test
  void reportingFaultMakesEquipmentUnavailableAndSavesBothRecords() {
    MaintenanceCase maintenanceCase = report();
    FaultReport report = faultReports.findById(REPORT_ID).orElseThrow();

    assertThat(equipment.calls).containsExactly("exists", "under-maintenance");
    assertThat(report.faultCategory()).isEqualTo("electrical");
    assertThat(report.description()).isEqualTo("Does not start");
    assertThat(report.priority()).isEqualTo("urgent");
    assertThat(report.reporterId()).isEqualTo(REPORTER_ID);
    assertThat(report.reportedAt()).isEqualTo(NOW);
    assertThat(maintenanceCase.maintenanceCaseId()).isEqualTo(CASE_ID);
    assertThat(maintenanceCase.faultReportId()).isEqualTo(REPORT_ID);
    assertThat(maintenanceCase.status()).isEqualTo(MaintenanceStatus.REPORTED);
    assertThat(cases.findById(CASE_ID)).isPresent();
    assertThat(cases.saveCount).isEqualTo(1);
    assertThat(history.entries).hasSize(1);
    assertThat(history.entries.get(0).action()).isEqualTo(MaintenanceHistoryAction.REPORT_FAULT);
    assertThat(history.entries.get(0).fromStatus()).isNull();
    assertThat(history.entries.get(0).toStatus()).isEqualTo(MaintenanceStatus.REPORTED);
    assertThat(history.entries.get(0).actorId()).isEqualTo(REPORTER_ID);
    assertThat(history.entries.get(0).occurredAt()).isEqualTo(NOW);
  }

  @Test
  void invalidFaultIsRejectedBeforeEquipmentOrPersistenceCalls() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> service.reportFault(EQUIPMENT_ID, " ", "Fault", REPORTER_ID, "urgent"));
    assertThat(equipment.calls).isEmpty();
    assertThat(faultReports.saved).isEmpty();
    assertThat(cases.saved).isEmpty();
    assertThat(history.entries).isEmpty();
  }

  @Test
  void assigningValidatesTechnicianPersistsCaseAndPublishesEvent() {
    report();
    service.assignTechnician(CASE_ID, TECHNICIAN_ID, ACTOR_ID);

    assertThat(technicians.checked).containsExactly(TECHNICIAN_ID);
    assertThat(cases.findById(CASE_ID).orElseThrow().status())
        .isEqualTo(MaintenanceStatus.ASSIGNED);
    assertThat(cases.findById(CASE_ID).orElseThrow().assignedTechnicianId())
        .isEqualTo(TECHNICIAN_ID);
    assertThat(cases.findById(CASE_ID).orElseThrow().assignedAt()).isEqualTo(NOW);
    assertThat(cases.saveCount).isEqualTo(2);
    assertThat(history.entries.get(1).action()).isEqualTo(MaintenanceHistoryAction.ASSIGN);
    assertThat(history.entries.get(1).actorId()).isEqualTo(ACTOR_ID);
    assertThat(events.assigned).containsExactly(
        new MaintenanceAssigned(CASE_ID, EQUIPMENT_ID, TECHNICIAN_ID, ACTOR_ID, NOW));
  }

  @Test
  void rejectedTechnicianCannotChangeCaseOrPublishEvent() {
    report();
    technicians.reject = true;

    assertThatIllegalArgumentException()
        .isThrownBy(() -> service.assignTechnician(CASE_ID, TECHNICIAN_ID, ACTOR_ID));
    assertThat(cases.findById(CASE_ID).orElseThrow().status())
        .isEqualTo(MaintenanceStatus.REPORTED);
    assertThat(cases.saveCount).isEqualTo(1);
    assertThat(events.assigned).isEmpty();
  }

  @Test
  void repeatedAssignmentIsRejectedWithoutAnotherHistoryEntryOrEvent() {
    assigned();

    assertThatThrownBy(() -> service.assignTechnician(CASE_ID, TECHNICIAN_ID, ACTOR_ID))
        .isInstanceOf(InvalidMaintenanceTransitionException.class);
    assertThat(history.entries).hasSize(2);
    assertThat(events.assigned).hasSize(1);
  }

  @Test
  void returnedCaseIsAnIndependentSnapshot() {
    report();
    MaintenanceCase returned = service.getCase(CASE_ID);
    returned.assign(TECHNICIAN_ID, NOW);

    assertThat(cases.findById(CASE_ID).orElseThrow().status())
        .isEqualTo(MaintenanceStatus.REPORTED);
    assertThat(history.entries).hasSize(1);
  }

  @Test
  void startingAndRecordingWorkPersistValuesAndBusinessHistory() {
    started();
    service.recordDiagnosis(CASE_ID, "  Loose wire  ", TECHNICIAN_ID);
    service.recordRepairAction(CASE_ID, "  Replaced wire  ", TECHNICIAN_ID);
    service.addMaintenanceNote(CASE_ID, "  Final check  ", TECHNICIAN_ID);

    MaintenanceCase stored = cases.findById(CASE_ID).orElseThrow();
    assertThat(stored.status()).isEqualTo(MaintenanceStatus.IN_PROGRESS);
    assertThat(stored.startedAt()).isEqualTo(NOW);
    assertThat(stored.diagnosis()).isEqualTo("Loose wire");
    assertThat(stored.repairAction()).isEqualTo("Replaced wire");
    assertThat(stored.maintenanceNotes()).containsExactly("Final check");
    assertThat(history.entries.stream().map(MaintenanceHistoryEntry::action))
        .containsExactly(
            MaintenanceHistoryAction.REPORT_FAULT,
            MaintenanceHistoryAction.ASSIGN,
            MaintenanceHistoryAction.START,
            MaintenanceHistoryAction.RECORD_DIAGNOSIS,
            MaintenanceHistoryAction.RECORD_REPAIR_ACTION,
            MaintenanceHistoryAction.ADD_NOTE);
    assertThat(history.entries.get(5).actorId()).isEqualTo(TECHNICIAN_ID);
    assertThat(history.entries.get(5).occurredAt()).isEqualTo(NOW);
    assertThat(history.entries.get(5).detail()).isEqualTo("Final check");
  }

  @Test
  void resolvingLeavesEquipmentUnavailableUntilClosure() {
    started();
    service.resolve(CASE_ID, "  Repaired  ", TECHNICIAN_ID);

    assertThat(cases.findById(CASE_ID).orElseThrow().status())
        .isEqualTo(MaintenanceStatus.RESOLVED);
    assertThat(cases.findById(CASE_ID).orElseThrow().completedAt()).isEqualTo(NOW);
    assertThat(equipment.calls).containsExactly("exists", "under-maintenance");
    assertThat(events.completed).isEmpty();
    assertThat(history.entries.get(3).action()).isEqualTo(MaintenanceHistoryAction.RESOLVE);
  }

  @Test
  void closingResolvedCaseRestoresAvailabilityAndPublishesCompletion() {
    started();
    service.resolve(CASE_ID, "Repaired", TECHNICIAN_ID);
    service.close(CASE_ID, ACTOR_ID);

    assertThat(equipment.calls).containsExactly("exists", "under-maintenance", "available");
    assertThat(cases.findById(CASE_ID).orElseThrow().status())
        .isEqualTo(MaintenanceStatus.CLOSED);
    assertThat(cases.findById(CASE_ID).orElseThrow().closedAt()).isEqualTo(NOW);
    assertThat(events.completed).containsExactly(
        new MaintenanceCompleted(
            CASE_ID, EQUIPMENT_ID, TECHNICIAN_ID, ACTOR_ID, MaintenanceStatus.RESOLVED, NOW));
    assertThat(history.entries.get(4).fromStatus()).isEqualTo(MaintenanceStatus.RESOLVED);
    assertThat(history.entries.get(4).toStatus()).isEqualTo(MaintenanceStatus.CLOSED);
  }

  @Test
  void closingNotRepairableCaseRetiresEquipment() {
    started();
    service.markNotRepairable(CASE_ID, "No parts", TECHNICIAN_ID);
    assertThat(equipment.calls).containsExactly("exists", "under-maintenance");
    service.close(CASE_ID, ACTOR_ID);

    assertThat(equipment.calls).containsExactly("exists", "under-maintenance", "retired");
    assertThat(events.completed).containsExactly(
        new MaintenanceCompleted(
            CASE_ID, EQUIPMENT_ID, TECHNICIAN_ID, ACTOR_ID,
            MaintenanceStatus.NOT_REPAIRABLE, NOW));
    assertThat(history.entries.get(3).action())
        .isEqualTo(MaintenanceHistoryAction.MARK_NOT_REPAIRABLE);
  }

  @Test
  void illegalCloseCannotMakeEquipmentAvailable() {
    started();
    assertThatThrownBy(() -> service.close(CASE_ID, ACTOR_ID))
        .isInstanceOf(InvalidMaintenanceTransitionException.class);
    assertThat(equipment.calls).containsExactly("exists", "under-maintenance");
    assertThat(cases.findById(CASE_ID).orElseThrow().status())
        .isEqualTo(MaintenanceStatus.IN_PROGRESS);
    assertThat(events.completed).isEmpty();
  }

  @Test
  void historyQueriesReturnRetainedChronologicalEntries() {
    started();
    List<MaintenanceHistoryEntry> byEquipment =
        service.getEquipmentMaintenanceHistory(EQUIPMENT_ID);
    assertThat(byEquipment).hasSize(3);
    assertThat(byEquipment).isEqualTo(service.getCaseHistory(CASE_ID));
    assertThat(byEquipment.stream().map(MaintenanceHistoryEntry::action))
        .containsExactly(
            MaintenanceHistoryAction.REPORT_FAULT,
            MaintenanceHistoryAction.ASSIGN,
            MaintenanceHistoryAction.START);
    assertThatThrownBy(() -> byEquipment.clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void unknownCaseThrowsTypedNotFoundException() {
    assertThatThrownBy(() -> service.getCase(CASE_ID))
        .isInstanceOf(MaintenanceCaseNotFoundException.class)
        .satisfies(failure -> assertThat(((MaintenanceCaseNotFoundException) failure)
            .getMaintenanceCaseId()).isEqualTo(CASE_ID));
  }

  @Test
  void equipmentPortFailureIsPropagatedBeforeSavingFault() {
    equipment.failUnderMaintenance = true;
    assertThatThrownBy(this::report).hasMessage("equipment unavailable");
    assertThat(faultReports.saved).isEmpty();
    assertThat(cases.saved).isEmpty();
    assertThat(history.entries).isEmpty();
  }

  @Test
  void closurePortFailureIsPropagatedWithoutSavingClosureOrEvent() {
    started();
    service.resolve(CASE_ID, "Repaired", TECHNICIAN_ID);
    equipment.failAvailable = true;

    assertThatThrownBy(() -> service.close(CASE_ID, ACTOR_ID)).hasMessage("equipment unavailable");
    assertThat(cases.findById(CASE_ID).orElseThrow().status())
        .isEqualTo(MaintenanceStatus.RESOLVED);
    assertThat(history.entries).hasSize(4);
    assertThat(events.completed).isEmpty();
  }

  private static final class FakeFaultReports implements FaultReportRepository {
    final Map<UUID, FaultReport> saved = new HashMap<>();

    public void save(FaultReport report) {
      saved.put(report.faultReportId(), report);
    }

    public Optional<FaultReport> findById(UUID id) {
      return Optional.ofNullable(saved.get(id));
    }
  }

  private static final class FakeCases implements MaintenanceCaseRepository {
    final Map<UUID, MaintenanceCase> saved = new HashMap<>();
    int saveCount;

    public void save(MaintenanceCase maintenanceCase) {
      saved.put(maintenanceCase.maintenanceCaseId(), maintenanceCase.copy());
      saveCount++;
    }

    public Optional<MaintenanceCase> findById(UUID id) {
      return Optional.ofNullable(saved.get(id)).map(MaintenanceCase::copy);
    }
  }

  private static final class FakeHistory implements MaintenanceHistoryRepository {
    final List<MaintenanceHistoryEntry> entries = new ArrayList<>();

    public void append(MaintenanceHistoryEntry entry) {
      entries.add(entry);
    }

    public List<MaintenanceHistoryEntry> findByEquipmentId(UUID id) {
      return entries.stream().filter(entry -> entry.equipmentId().equals(id)).toList();
    }

    public List<MaintenanceHistoryEntry> findByCaseId(UUID id) {
      return entries.stream().filter(entry -> entry.maintenanceCaseId().equals(id)).toList();
    }
  }

  private static final class FakeEquipment implements EquipmentMaintenancePort {
    final List<String> calls = new ArrayList<>();
    boolean failUnderMaintenance;
    boolean failAvailable;

    public void ensureEquipmentExists(UUID id) {
      assertThat(id).isEqualTo(EQUIPMENT_ID);
      calls.add("exists");
    }

    public void markUnderMaintenance(UUID id) {
      assertThat(id).isEqualTo(EQUIPMENT_ID);
      if (failUnderMaintenance) throw new IllegalStateException("equipment unavailable");
      calls.add("under-maintenance");
    }

    public void markAvailable(UUID id) {
      assertThat(id).isEqualTo(EQUIPMENT_ID);
      if (failAvailable) throw new IllegalStateException("equipment unavailable");
      calls.add("available");
    }

    public void markRetired(UUID id) {
      assertThat(id).isEqualTo(EQUIPMENT_ID);
      calls.add("retired");
    }
  }

  private static final class FakeTechnicians implements TechnicianDirectoryPort {
    final List<UUID> checked = new ArrayList<>();
    boolean reject;

    public void ensureTechnician(UUID userId) {
      checked.add(userId);
      if (reject) throw new IllegalArgumentException("not a technician");
    }
  }

  private static final class FakeEvents implements MaintenanceEventPublisher {
    final List<MaintenanceAssigned> assigned = new ArrayList<>();
    final List<MaintenanceCompleted> completed = new ArrayList<>();

    public void publish(MaintenanceAssigned event) {
      assigned.add(event);
    }

    public void publish(MaintenanceCompleted event) {
      completed.add(event);
    }
  }
}
