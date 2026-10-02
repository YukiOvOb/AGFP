package sg.edu.nus.serms.maintenance.service;

import static org.assertj.core.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import sg.edu.nus.serms.maintenance.domain.*;
import sg.edu.nus.serms.maintenance.repository.*;

class MaintenanceServiceTest {
  private static final UUID CASE = new UUID(0, 1);
  private static final UUID EQUIPMENT = new UUID(0, 2);
  private static final UUID REPORTER = new UUID(0, 3);
  private static final UUID TECHNICIAN = new UUID(0, 4);
  private static final UUID ACTOR = new UUID(0, 5);
  private static final UUID LOAN = new UUID(0, 6);
  private static final Instant NOW = Instant.parse("2026-10-02T02:00:00Z");
  private final List<String> calls = new ArrayList<>();
  private final FakeCases cases = new FakeCases();
  private final FakeHistory history = new FakeHistory();
  private final FakeEquipment equipment = new FakeEquipment();
  private final FakeTechnicians technicians = new FakeTechnicians();
  private final FakeEvents events = new FakeEvents();
  private final MaintenanceService service = new MaintenanceService(cases, history, equipment,
      technicians, events, () -> CASE, Clock.fixed(NOW, ZoneOffset.UTC));

  private MaintenanceCase report() { return service.reportFault(EQUIPMENT, REPORTER, "  Broken cable  "); }

  private void started() {
    report();
    service.assignTechnician(CASE, TECHNICIAN, ACTOR);
    service.startMaintenance(CASE, TECHNICIAN);
  }

  @Test
  void faultCreatesOneOpenCaseAndMarksEquipmentUnderMaintenance() {
    var item = report();
    assertThat(calls).containsExactly("exists", "under-maintenance", "save", "history");
    assertThat(cases.saved).hasSize(1);
    assertThat(item.status()).isEqualTo(MaintenanceStatus.OPEN);
    assertThat(item.reportedBy()).isEqualTo(REPORTER);
    assertThat(item.reportedAt()).isEqualTo(NOW);
    assertThat(item.faultDescription()).isEqualTo("Broken cable");
    assertThat(item.loanId()).isNull();
    assertThat(equipment.state).isEqualTo("UNDER_MAINTENANCE");
    assertThat(history.entries).containsExactly(new MaintenanceHistoryEntry(CASE, EQUIPMENT,
        MaintenanceHistoryAction.REPORT_FAULT, null, MaintenanceStatus.OPEN, REPORTER, NOW, "Broken cable"));
  }

  @Test
  void faultCanRetainOptionalLoan() {
    assertThat(service.reportFault(EQUIPMENT, REPORTER, "Fault", LOAN).loanId()).isEqualTo(LOAN);
    assertThat(cases.findById(CASE).orElseThrow().loanId()).isEqualTo(LOAN);
  }

  @Test
  void invalidFaultHasNoEquipmentOrPersistenceSideEffects() {
    assertThatIllegalArgumentException().isThrownBy(() -> service.reportFault(EQUIPMENT, REPORTER, " "));
    assertThat(calls).isEmpty();
    assertThat(cases.saved).isEmpty();
  }

  @Test
  void assignmentValidatesMaintainerAndPublishesEvent() {
    report();
    calls.clear();
    var assigned = service.assignTechnician(CASE, TECHNICIAN, ACTOR);
    assertThat(calls).containsExactly("technician", "save", "history", "assigned-event");
    assertThat(assigned.status()).isEqualTo(MaintenanceStatus.ASSIGNED);
    assertThat(assigned.assignedTo()).isEqualTo(TECHNICIAN);
    assertThat(technicians.checked).containsExactly(TECHNICIAN);
    assertThat(events.assigned).containsExactly(new MaintenanceAssigned(CASE, EQUIPMENT, TECHNICIAN, ACTOR, NOW));
    assertThat(history.entries.get(1).actorId()).isEqualTo(ACTOR);
  }

  @Test
  void invalidMaintainerCannotMutateCaseOrPublish() {
    report();
    technicians.reject = true;
    var before = service.getCase(CASE).snapshot();
    assertThatIllegalArgumentException().isThrownBy(() -> service.assignTechnician(CASE, TECHNICIAN, ACTOR));
    assertThat(service.getCase(CASE).snapshot()).isEqualTo(before);
    assertThat(history.entries).hasSize(1);
    assertThat(events.assigned).isEmpty();
  }

  @Test
  void repeatedAssignmentAndPrematureStartAreRejected() {
    report();
    assertThatThrownBy(() -> service.startMaintenance(CASE, ACTOR))
        .isInstanceOf(InvalidMaintenanceTransitionException.class);
    service.assignTechnician(CASE, TECHNICIAN, ACTOR);
    assertThatThrownBy(() -> service.assignTechnician(CASE, TECHNICIAN, ACTOR))
        .isInstanceOf(InvalidMaintenanceTransitionException.class);
    assertThat(history.entries).hasSize(2);
    assertThat(events.assigned).hasSize(1);
  }

  @ParameterizedTest
  @EnumSource(value = MaintenanceHistoryAction.class,
      names = {"RECORD_DIAGNOSIS", "RECORD_REPAIR_ACTION", "ADD_NOTE"})
  void workDetailsAppendHistoryWithoutSavingOrChangingDurableState(MaintenanceHistoryAction action) {
    started();
    var before = service.getCase(CASE).snapshot();
    calls.clear();
    var result = work(action, "  Detail  ");
    assertThat(calls).containsExactly("history");
    assertThat(result.snapshot()).isEqualTo(before);
    assertThat(service.getCase(CASE).snapshot()).isEqualTo(before);
    assertThat(history.entries.get(3)).isEqualTo(new MaintenanceHistoryEntry(CASE, EQUIPMENT, action,
        MaintenanceStatus.IN_PROGRESS, MaintenanceStatus.IN_PROGRESS, TECHNICIAN, NOW, "Detail"));
  }

  static Stream<Arguments> invalidWorkStates() {
    return Stream.of(MaintenanceStatus.OPEN, MaintenanceStatus.ASSIGNED,
        MaintenanceStatus.RESOLVED, MaintenanceStatus.UNREPAIRABLE)
        .flatMap(status -> Stream.of(MaintenanceHistoryAction.RECORD_DIAGNOSIS,
            MaintenanceHistoryAction.RECORD_REPAIR_ACTION, MaintenanceHistoryAction.ADD_NOTE)
            .map(action -> Arguments.of(status, action)));
  }

  @ParameterizedTest
  @MethodSource("invalidWorkStates")
  void workRequiresInProgress(MaintenanceStatus status, MaintenanceHistoryAction action) {
    var item = new MaintenanceCase(CASE, EQUIPMENT, REPORTER, null, "Fault", NOW);
    if (status != MaintenanceStatus.OPEN) item.assign(TECHNICIAN);
    if (status == MaintenanceStatus.RESOLVED || status == MaintenanceStatus.UNREPAIRABLE) {
      item.start();
      if (status == MaintenanceStatus.RESOLVED) item.resolve("Repaired", NOW);
      else item.markUnrepairable("No parts", NOW);
    }
    cases.saved.put(CASE, item);
    assertThatThrownBy(() -> work(action, "Detail")).isInstanceOf(IllegalStateException.class);
    assertThat(history.entries).isEmpty();
    assertThat(calls).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(value = MaintenanceHistoryAction.class,
      names = {"RECORD_DIAGNOSIS", "RECORD_REPAIR_ACTION", "ADD_NOTE"})
  void blankWorkDetailsAreRejectedWithoutHistory(MaintenanceHistoryAction action) {
    started();
    calls.clear();
    assertThatIllegalArgumentException().isThrownBy(() -> work(action, " "));
    assertThatIllegalArgumentException().isThrownBy(() -> work(action, null));
    assertThat(calls).isEmpty();
    assertThat(history.entries).hasSize(3);
  }

  private MaintenanceCase work(MaintenanceHistoryAction action, String detail) {
    return switch (action) {
      case RECORD_DIAGNOSIS -> service.recordDiagnosis(CASE, detail, TECHNICIAN);
      case RECORD_REPAIR_ACTION -> service.recordRepairAction(CASE, detail, TECHNICIAN);
      case ADD_NOTE -> service.addMaintenanceNote(CASE, detail, TECHNICIAN);
      default -> throw new AssertionError("Not a work action");
    };
  }

  static Stream<Arguments> precedenceScenarios() {
    return Stream.of(Arguments.of(false, false, false, "AVAILABLE"),
        Arguments.of(false, true, false, "ON_LOAN"),
        Arguments.of(true, true, false, "UNDER_MAINTENANCE"),
        Arguments.of(true, true, true, "RETIRED"));
  }

  @ParameterizedTest
  @MethodSource("precedenceScenarios")
  void resolveSavesTerminalCaseBeforeImmediateEquipmentRecomputation(
      boolean otherCase, boolean loan, boolean retired, String expected) {
    started();
    equipment.otherActiveCase = otherCase;
    equipment.activeLoan = loan;
    if (retired) equipment.state = "RETIRED";
    calls.clear();
    var result = service.resolve(CASE, "  Repaired  ", TECHNICIAN);
    assertThat(calls).containsExactly("save", "recompute", "history", "completed-event");
    assertThat(result.status()).isEqualTo(MaintenanceStatus.RESOLVED);
    assertThat(result.resolutionNote()).isEqualTo("Repaired");
    assertThat(result.resolvedAt()).isEqualTo(NOW);
    assertThat(equipment.state).isEqualTo(expected);
    assertThat(events.completed).containsExactly(new MaintenanceCompleted(CASE, EQUIPMENT,
        TECHNICIAN, TECHNICIAN, MaintenanceStatus.RESOLVED, NOW));
  }

  @Test
  void unrepairableImmediatelyRetiresEquipmentAndPublishesResult() {
    started();
    equipment.activeLoan = true;
    calls.clear();
    var result = service.markUnrepairable(CASE, "  No parts  ", TECHNICIAN);
    assertThat(calls).containsExactly("save", "retired", "history", "completed-event");
    assertThat(result.status()).isEqualTo(MaintenanceStatus.UNREPAIRABLE);
    assertThat(result.resolutionNote()).isEqualTo("No parts");
    assertThat(result.resolvedAt()).isEqualTo(NOW);
    assertThat(equipment.state).isEqualTo("RETIRED");
    assertThat(equipment.activeLoan).isTrue();
    assertThat(events.completed).containsExactly(new MaintenanceCompleted(CASE, EQUIPMENT,
        TECHNICIAN, TECHNICIAN, MaintenanceStatus.UNREPAIRABLE, NOW));
    assertThat(history.entries.get(3).action()).isEqualTo(MaintenanceHistoryAction.MARK_UNREPAIRABLE);
  }

  @Test
  void invalidCompletionProducesNoWritesOrEvents() {
    started();
    calls.clear();
    assertThatIllegalArgumentException().isThrownBy(() -> service.resolve(CASE, " ", TECHNICIAN));
    assertThatIllegalArgumentException().isThrownBy(() -> service.markUnrepairable(CASE, null, TECHNICIAN));
    assertThat(calls).isEmpty();
    assertThat(events.completed).isEmpty();
  }

  @Test
  void repeatedCompletionIsRejectedWithoutAnotherEvent() {
    started();
    service.resolve(CASE, "Repaired", TECHNICIAN);
    calls.clear();
    assertThatThrownBy(() -> service.markUnrepairable(CASE, "No parts", TECHNICIAN))
        .isInstanceOf(InvalidMaintenanceTransitionException.class);
    assertThat(calls).isEmpty();
    assertThat(events.completed).hasSize(1);
  }

  @Test
  void unknownCaseUsesTypedException() {
    assertThatThrownBy(() -> service.getCase(CASE)).isInstanceOf(MaintenanceCaseNotFoundException.class)
        .satisfies(error -> assertThat(((MaintenanceCaseNotFoundException) error).getMaintenanceCaseId()).isEqualTo(CASE));
  }

  @Test
  void returnsIndependentCasesAndImmutableLogicalHistory() {
    var returned = report();
    returned.assign(TECHNICIAN);
    assertThat(service.getCase(CASE).status()).isEqualTo(MaintenanceStatus.OPEN);
    assertThat(service.getEquipmentMaintenanceHistory(EQUIPMENT)).isEqualTo(service.getCaseHistory(CASE));
    assertThatThrownBy(() -> service.getCaseHistory(CASE).clear()).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void equipmentFailureStopsFaultCreation() {
    equipment.fail = true;
    assertThatThrownBy(this::report).hasMessage("Equipment failure");
    assertThat(cases.saved).isEmpty();
    assertThat(history.entries).isEmpty();
  }

  @Test
  void exposesNoSeparateFinalizationOperation() {
    assertThat(MaintenanceService.class.getDeclaredMethods()).noneMatch(method -> method.getName().equals("close"));
  }

  private final class FakeCases implements MaintenanceCaseRepository {
    final Map<UUID, MaintenanceCase> saved = new HashMap<>();
    public void save(MaintenanceCase item) {
      calls.add("save");
      saved.put(item.maintenanceCaseId(), item.copy());
    }
    public Optional<MaintenanceCase> findById(UUID id) { return Optional.ofNullable(saved.get(id)).map(MaintenanceCase::copy); }
  }

  private final class FakeHistory implements MaintenanceHistoryRepository {
    final List<MaintenanceHistoryEntry> entries = new ArrayList<>();
    public void append(MaintenanceHistoryEntry entry) { calls.add("history"); entries.add(entry); }
    public List<MaintenanceHistoryEntry> findByEquipmentId(UUID id) {
      return entries.stream().filter(entry -> entry.equipmentId().equals(id)).toList();
    }
    public List<MaintenanceHistoryEntry> findByCaseId(UUID id) {
      return entries.stream().filter(entry -> entry.maintenanceCaseId().equals(id)).toList();
    }
  }

  private final class FakeEquipment implements EquipmentMaintenancePort {
    String state = "AVAILABLE";
    boolean otherActiveCase, activeLoan, fail;
    public void ensureEquipmentExists(UUID id) { calls.add("exists"); }
    public void markUnderMaintenance(UUID id) {
      if (fail) throw new IllegalStateException("Equipment failure");
      calls.add("under-maintenance");
      state = "UNDER_MAINTENANCE";
    }
    public void recomputeAfterResolvedMaintenance(UUID id) {
      assertThat(cases.findById(CASE).orElseThrow().status()).isEqualTo(MaintenanceStatus.RESOLVED);
      calls.add("recompute");
      if (!state.equals("RETIRED")) state = otherActiveCase ? "UNDER_MAINTENANCE" : activeLoan ? "ON_LOAN" : "AVAILABLE";
    }
    public void markRetired(UUID id) {
      assertThat(cases.findById(CASE).orElseThrow().status()).isEqualTo(MaintenanceStatus.UNREPAIRABLE);
      calls.add("retired"); state = "RETIRED";
    }
  }

  private final class FakeTechnicians implements TechnicianDirectoryPort {
    final List<UUID> checked = new ArrayList<>();
    boolean reject;
    public void ensureTechnician(UUID id) {
      calls.add("technician");
      checked.add(id);
      if (reject) throw new IllegalArgumentException("Not an ACTIVE MAINTAINER");
    }
  }

  private final class FakeEvents implements MaintenanceEventPublisher {
    final List<MaintenanceAssigned> assigned = new ArrayList<>();
    final List<MaintenanceCompleted> completed = new ArrayList<>();
    public void publish(MaintenanceAssigned event) { calls.add("assigned-event"); assigned.add(event); }
    public void publish(MaintenanceCompleted event) { calls.add("completed-event"); completed.add(event); }
  }
}
