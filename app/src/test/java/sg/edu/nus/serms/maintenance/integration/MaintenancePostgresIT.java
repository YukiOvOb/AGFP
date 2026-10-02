package sg.edu.nus.serms.maintenance.integration;

import static org.assertj.core.api.Assertions.*;

import java.sql.Timestamp;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import sg.edu.nus.serms.maintenance.domain.*;
import sg.edu.nus.serms.maintenance.repository.MaintenanceVersionConflictException;
import sg.edu.nus.serms.maintenance.service.InvalidMaintenanceTechnicianException;

/** Explicit Failsafe profile only. All queries and writes use real V002 PostgreSQL. */
@Execution(ExecutionMode.SAME_THREAD)
class MaintenancePostgresIT {
  private Uc04PostgresTestSupport db;
  private UUID reporter, technician, equipment;

  @BeforeEach
  void fixtures() {
    db = new Uc04PostgresTestSupport();
    reporter = db.user("ACTIVE", "BORROWER");
    technician = db.user("ACTIVE", "MAINTAINER");
    equipment = db.equipment();
  }

  @AfterEach
  void cleanup() {
    if (db != null) db.close();
  }

  @Test
  void reportFaultCommitsOpenCaseEquipmentAndAudit() {
    assertThat(AopUtils.isCglibProxy(db.service)).isTrue();
    var item = db.report(equipment, reporter);
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    assertThat(item.maintenanceCaseId()).isIn(db.identifiers.created);
    assertThat(item.equipmentId()).isEqualTo(equipment);
    assertThat(item.reportedBy()).isEqualTo(reporter);
    assertThat(item.status()).isEqualTo(MaintenanceStatus.OPEN);
    assertThat(item.faultDescription()).isEqualTo("Broken cable");
    assertThat(item.reportedAt()).isEqualTo(db.clock.instant());
    assertThat(item.assignedTo()).isNull();
    assertThat(item.loanId()).isNull();
    assertThat(item.version()).isZero();
    assertPersisted(item);
    assertThat(db.equipmentStatus(equipment)).isEqualTo("UNDER_MAINTENANCE");
    assertThat(db.equipmentVersion(equipment)).isEqualTo(1);
    var audit = db.jdbc.queryForMap("""
        SELECT * FROM serms.audit_log WHERE entity_type = 'MaintenanceCase' AND entity_id = ?
        """, item.maintenanceCaseId());
    assertThat(audit).containsEntry("action", "MAINTENANCE_REPORT_FAULT")
        .containsEntry("entity_type", "MaintenanceCase").containsEntry("entity_id", item.maintenanceCaseId())
        .containsEntry("actor_id", reporter).containsEntry("outcome", "SUCCESS")
        .containsEntry("change_summary", "Broken cable");
    assertThat(audit.get("request_id")).isEqualTo("maintenance:" + audit.get("audit_id"));
    assertThat(audit.get("occurred_at")).isEqualTo(Timestamp.from(item.reportedAt()));
    assertThat(db.events.assigned).isEmpty();
    assertThat(db.events.completed).isEmpty();
  }

  @Test
  void activeMaintainerAssignmentUsesRealVersionTriggerAndSpringEvent() {
    var open = db.report(equipment, reporter);
    db.clock.advance();
    var assigned = db.service.assignTechnician(open.maintenanceCaseId(), technician, reporter);
    assertThat(assigned.status()).isEqualTo(MaintenanceStatus.ASSIGNED);
    assertThat(assigned.assignedTo()).isEqualTo(technician);
    assertThat(assigned.version()).isEqualTo(1);
    assertPersisted(assigned);
    assertThat(db.actionCount(open.maintenanceCaseId(), "MAINTENANCE_ASSIGN")).isEqualTo(1);
    assertThat(db.events.assigned).containsExactly(new MaintenanceAssigned(open.maintenanceCaseId(),
        equipment, technician, reporter, db.clock.instant()));
    assertThat(db.service.getCaseHistory(open.maintenanceCaseId()).get(1)).isEqualTo(
        new MaintenanceHistoryEntry(open.maintenanceCaseId(), equipment, MaintenanceHistoryAction.ASSIGN,
            MaintenanceStatus.OPEN, MaintenanceStatus.ASSIGNED, reporter, db.clock.instant(), null));
  }

  @ParameterizedTest
  @ValueSource(strings = {"BORROWER_ONLY", "DISABLED_MAINTAINER", "MISSING"})
  void invalidTechnicianLeavesCaseAndAuditUnchanged(String condition) {
    UUID invalid = switch (condition) {
      case "BORROWER_ONLY" -> reporter;
      case "DISABLED_MAINTAINER" -> db.user("DISABLED", "MAINTAINER");
      default -> UUID.randomUUID();
    };
    var before = db.report(equipment, reporter);
    assertThatThrownBy(() -> db.service.assignTechnician(before.maintenanceCaseId(), invalid, reporter))
        .isInstanceOf(InvalidMaintenanceTechnicianException.class);
    assertPersisted(before);
    assertThat(db.actionCount(before.maintenanceCaseId(), "MAINTENANCE_ASSIGN")).isZero();
    assertThat(db.history.findByCaseId(before.maintenanceCaseId())).hasSize(1);
    assertThat(db.events.assigned).isEmpty();
  }

  @Test
  void startCommitsInProgressAndVersionZeroOneTwo() {
    var open = db.report(equipment, reporter);
    assertThat(open.version()).isZero();
    db.clock.advance();
    var assigned = db.service.assignTechnician(open.maintenanceCaseId(), technician, reporter);
    assertThat(assigned.version()).isEqualTo(1);
    db.clock.advance();
    var started = db.service.startMaintenance(open.maintenanceCaseId(), technician);
    assertThat(started.status()).isEqualTo(MaintenanceStatus.IN_PROGRESS);
    assertThat(started.version()).isEqualTo(2);
    assertPersisted(started);
    assertThat(db.history.findByCaseId(open.maintenanceCaseId())).extracting(MaintenanceHistoryEntry::action)
        .containsExactly(MaintenanceHistoryAction.REPORT_FAULT, MaintenanceHistoryAction.ASSIGN, MaintenanceHistoryAction.START);
    assertThat(db.equipmentStatus(equipment)).isEqualTo("UNDER_MAINTENANCE");
  }

  @Test
  void resolveWithoutOtherFactsRestoresAvailableAndPublishesCompletion() {
    var started = db.started(equipment, reporter, technician);
    db.clock.advance();
    var resolved = db.service.resolve(started.maintenanceCaseId(), "  Cable repaired  ", technician);
    assertResolved(started, resolved);
    assertThat(db.equipmentStatus(equipment)).isEqualTo("AVAILABLE");
    assertThat(db.events.completed).containsExactly(completion(resolved));
    assertThat(db.actionCount(started.maintenanceCaseId(), "MAINTENANCE_RESOLVE")).isEqualTo(1);
  }

  @Test
  void resolvedLinkedCaseRestoresOnLoanWhenActiveLoanRemains() {
    UUID loan = db.activeLoan(equipment, reporter);
    db.clock.advance();
    var open = db.service.reportFault(equipment, reporter, "Broken cable", loan);
    assertThat(open.loanId()).isEqualTo(loan);
    assertPersisted(open);
    db.clock.advance();
    db.service.assignTechnician(open.maintenanceCaseId(), technician, reporter);
    db.clock.advance();
    var started = db.service.startMaintenance(open.maintenanceCaseId(), technician);
    assertThat(db.equipmentStatus(equipment)).isEqualTo("UNDER_MAINTENANCE");
    db.clock.advance();
    var resolved = db.service.resolve(open.maintenanceCaseId(), "Cable repaired", technician);
    assertResolved(started, resolved);
    assertThat(resolved.loanId()).isEqualTo(loan);
    assertThat(db.equipmentStatus(equipment)).isEqualTo("ON_LOAN");
    assertThat(db.jdbc.queryForObject("SELECT status FROM serms.loan WHERE loan_id = ?", String.class, loan)).isEqualTo("ACTIVE");
  }

  @ParameterizedTest
  @EnumSource(value = MaintenanceStatus.class, names = {"OPEN", "ASSIGNED", "IN_PROGRESS"})
  void otherActiveCaseWinsOverActiveLoanAndAvoidsSameStateEquipmentUpdate(MaintenanceStatus otherState) {
    db.activeLoan(equipment, reporter);
    var first = db.started(equipment, reporter, technician);
    int equipmentVersion = db.equipmentVersion(equipment);
    var other = db.report(equipment, reporter);
    if (otherState != MaintenanceStatus.OPEN) {
      db.clock.advance();
      db.service.assignTechnician(other.maintenanceCaseId(), technician, reporter);
    }
    if (otherState == MaintenanceStatus.IN_PROGRESS) {
      db.clock.advance();
      db.service.startMaintenance(other.maintenanceCaseId(), technician);
    }
    db.clock.advance();
    var resolved = db.service.resolve(first.maintenanceCaseId(), "Cable repaired", technician);
    assertResolved(first, resolved);
    assertThat(db.cases.findById(other.maintenanceCaseId()).orElseThrow().status()).isEqualTo(otherState);
    assertThat(db.equipmentStatus(equipment)).isEqualTo("UNDER_MAINTENANCE");
    assertThat(db.equipmentVersion(equipment)).isEqualTo(equipmentVersion);
  }

  @Test
  void unrepairableRetiresEquipmentAndRecomputationNeverRestoresIt() {
    var started = db.started(equipment, reporter, technician);
    db.clock.advance();
    var terminal = db.service.markUnrepairable(started.maintenanceCaseId(), "  Parts unavailable  ", technician);
    assertThat(terminal.status()).isEqualTo(MaintenanceStatus.UNREPAIRABLE);
    assertThat(terminal.resolutionNote()).isEqualTo("Parts unavailable");
    assertThat(terminal.resolvedAt()).isEqualTo(db.clock.instant());
    assertThat(terminal.version()).isEqualTo(started.version() + 1);
    assertPersisted(terminal);
    assertThat(db.equipmentStatus(equipment)).isEqualTo("RETIRED");
    assertThat(db.events.completed).containsExactly(completion(terminal));
    assertThat(db.actionCount(terminal.maintenanceCaseId(), "MAINTENANCE_MARK_UNREPAIRABLE")).isEqualTo(1);
    int version = db.equipmentVersion(equipment);
    db.transactions.executeWithoutResult(tx -> db.equipment.recomputeAfterResolvedMaintenance(equipment));
    assertThat(db.equipmentStatus(equipment)).isEqualTo("RETIRED");
    assertThat(db.equipmentVersion(equipment)).isEqualTo(version);
    assertThatThrownBy(() -> db.service.reportFault(equipment, reporter, "Another fault"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("RETIRED");
    assertThat(db.equipmentVersion(equipment)).isEqualTo(version);
    assertThat(db.jdbc.queryForObject("SELECT count(*) FROM serms.maintenance_case WHERE equipment_id = ?",
        Integer.class, equipment)).isEqualTo(1);
  }

  @Test
  void staleSnapshotFailsRealOptimisticUpdateWithoutSecondHistoryOrEvent() {
    var open = db.report(equipment, reporter);
    UUID id = open.maintenanceCaseId(), otherTechnician = db.user("ACTIVE", "MAINTAINER");
    var first = db.cases.findById(id).orElseThrow();
    var stale = db.cases.findById(id).orElseThrow();
    assertThat(first).isNotSameAs(stale);
    assertThat(first.snapshot()).isEqualTo(stale.snapshot());
    db.reads.replayOnce.set(first);
    var assigned = db.service.assignTechnician(id, technician, reporter);
    assertThat(assigned.version()).isEqualTo(1);
    // Return the independently preloaded version-zero snapshot to the real service once.
    // All UPDATE, audit, equipment and event operations still use production components.
    db.reads.replayOnce.set(stale);
    assertThatThrownBy(() -> db.service.assignTechnician(id, otherTechnician, reporter))
        .isInstanceOf(MaintenanceVersionConflictException.class)
        .satisfies(error -> {
          var conflict = (MaintenanceVersionConflictException) error;
          assertThat(conflict.getMaintenanceCaseId()).isEqualTo(id);
          assertThat(conflict.getExpectedVersion()).isZero();
        });
    assertPersisted(assigned);
    assertThat(db.actionCount(id, "MAINTENANCE_ASSIGN")).isEqualTo(1);
    assertThat(db.events.assigned).containsExactly(new MaintenanceAssigned(id, equipment, technician,
        reporter, db.clock.instant()));
  }

  @Test
  void completionPublisherFailureRollsBackRealPostgresCaseEquipmentAndAudit() {
    var before = db.started(equipment, reporter, technician);
    int equipmentVersion = db.equipmentVersion(equipment);
    var publicationReached = new AtomicBoolean();
    db.publisher.beforeCompleted = event -> {
      publicationReached.set(true);
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      // These real JDBC reads join the service transaction and prove all three writes occurred.
      var uncommitted = db.cases.findById(before.maintenanceCaseId()).orElseThrow();
      assertThat(uncommitted.status()).isEqualTo(MaintenanceStatus.RESOLVED);
      assertThat(uncommitted.version()).isEqualTo(before.version() + 1);
      assertThat(db.equipmentStatus(equipment)).isEqualTo("AVAILABLE");
      assertThat(db.equipmentVersion(equipment)).isEqualTo(equipmentVersion + 1);
      assertThat(db.actionCount(before.maintenanceCaseId(), "MAINTENANCE_RESOLVE")).isEqualTo(1);
      throw new DeliberateCompletionFailure();
    };
    db.clock.advance();
    assertThatThrownBy(() -> db.service.resolve(before.maintenanceCaseId(), "Cable repaired", technician))
        .isInstanceOf(DeliberateCompletionFailure.class);
    assertThat(publicationReached).isTrue();
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    // Fresh connections outside any test transaction must observe the original committed state.
    assertPersisted(before);
    assertThat(db.cases.findById(before.maintenanceCaseId()).orElseThrow().resolutionNote()).isNull();
    assertThat(db.cases.findById(before.maintenanceCaseId()).orElseThrow().resolvedAt()).isNull();
    assertThat(db.equipmentStatus(equipment)).isEqualTo("UNDER_MAINTENANCE");
    assertThat(db.equipmentVersion(equipment)).isEqualTo(equipmentVersion);
    assertThat(db.actionCount(before.maintenanceCaseId(), "MAINTENANCE_RESOLVE")).isZero();
    assertThat(db.history.findByCaseId(before.maintenanceCaseId())).hasSize(3);
    assertThat(db.events.completed).isEmpty();
  }

  @Test
  void historyRoundTripsOrderedActionsActorsDetailsAndTransitionsAndFiltersGlobalAudit() {
    var started = db.started(equipment, reporter, technician);
    UUID id = started.maintenanceCaseId();
    db.unrelatedAudit(id, reporter);
    db.clock.advance();
    db.service.recordDiagnosis(id, "  Cable insulation split  ", technician);
    db.clock.advance();
    db.service.recordRepairAction(id, "Replaced cable", technician);
    db.clock.advance();
    db.service.addMaintenanceNote(id, "Safety check passed", technician);
    db.clock.advance();
    db.service.resolve(id, "Cable repaired", technician);
    var entries = db.history.findByCaseId(id);
    assertThat(entries).extracting(MaintenanceHistoryEntry::action).containsExactly(
        MaintenanceHistoryAction.REPORT_FAULT, MaintenanceHistoryAction.ASSIGN, MaintenanceHistoryAction.START,
        MaintenanceHistoryAction.RECORD_DIAGNOSIS, MaintenanceHistoryAction.RECORD_REPAIR_ACTION,
        MaintenanceHistoryAction.ADD_NOTE, MaintenanceHistoryAction.RESOLVE);
    assertThat(entries).extracting(MaintenanceHistoryEntry::actorId)
        .containsExactly(reporter, reporter, technician, technician, technician, technician, technician);
    assertThat(entries).extracting(MaintenanceHistoryEntry::detail).containsExactly(
        "Broken cable", null, null, "Cable insulation split", "Replaced cable", "Safety check passed", "Cable repaired");
    assertThat(entries).extracting(MaintenanceHistoryEntry::fromStatus).containsExactly(null,
        MaintenanceStatus.OPEN, MaintenanceStatus.ASSIGNED, MaintenanceStatus.IN_PROGRESS,
        MaintenanceStatus.IN_PROGRESS, MaintenanceStatus.IN_PROGRESS, MaintenanceStatus.IN_PROGRESS);
    assertThat(entries).extracting(MaintenanceHistoryEntry::toStatus).containsExactly(
        MaintenanceStatus.OPEN, MaintenanceStatus.ASSIGNED, MaintenanceStatus.IN_PROGRESS,
        MaintenanceStatus.IN_PROGRESS, MaintenanceStatus.IN_PROGRESS, MaintenanceStatus.IN_PROGRESS, MaintenanceStatus.RESOLVED);
    assertThat(entries).extracting(MaintenanceHistoryEntry::occurredAt).isSorted().doesNotHaveDuplicates();
    assertThat(entries).extracting(MaintenanceHistoryEntry::maintenanceCaseId).containsOnly(id);
    assertThat(entries).extracting(MaintenanceHistoryEntry::equipmentId).containsOnly(equipment);
    assertThat(db.actionCount(id, "UNRELATED_GLOBAL_ACTION")).isEqualTo(1);
    UUID otherEquipment = db.equipment();
    var unrelatedCase = db.report(otherEquipment, reporter);
    assertThat(db.history.findByEquipmentId(equipment)).containsExactlyElementsOf(entries);
    assertThat(db.history.findByEquipmentId(otherEquipment)).extracting(MaintenanceHistoryEntry::maintenanceCaseId)
        .containsExactly(unrelatedCase.maintenanceCaseId());
    assertThat(db.service.getCaseHistory(id)).containsExactlyElementsOf(entries);
  }

  @Test
  void competingServiceTransactionsLoadSameVersionAndOnlyOneAssignmentCommits() throws Exception {
    var open = db.report(equipment, reporter);
    UUID id = open.maintenanceCaseId(), otherTechnician = db.user("ACTIVE", "MAINTAINER");
    var coordination = new Uc04PostgresTestSupport.CompetingReads(id);
    db.reads.competing = coordination;
    db.clock.advance();
    var executor = Executors.newFixedThreadPool(2);
    List<AssignmentResult> results;
    try {
      var first = executor.submit(() -> assign(id, technician));
      var second = executor.submit(() -> assign(id, otherTechnician));
      results = List.of(first.get(40, TimeUnit.SECONDS), second.get(40, TimeUnit.SECONDS));
    } finally {
      db.reads.competing = null;
      coordination.barrier.reset();
      executor.shutdownNow();
      assertThat(executor.awaitTermination(40, TimeUnit.SECONDS)).isTrue();
    }
    assertThat(coordination.versions).containsExactlyInAnyOrder(0, 0);
    assertThat(coordination.backends).hasSize(2).doesNotHaveDuplicates();
    var successes = results.stream().filter(result -> result.persisted != null).toList();
    var conflicts = results.stream().filter(result -> result.conflict != null).toList();
    assertThat(successes).hasSize(1);
    assertThat(conflicts).hasSize(1);
    assertThat(conflicts.get(0).conflict.getMaintenanceCaseId()).isEqualTo(id);
    assertThat(conflicts.get(0).conflict.getExpectedVersion()).isZero();
    var winner = successes.get(0).persisted;
    assertThat(winner.status()).isEqualTo(MaintenanceStatus.ASSIGNED);
    assertThat(winner.version()).isEqualTo(1);
    assertPersisted(winner);
    assertThat(db.actionCount(id, "MAINTENANCE_ASSIGN")).isEqualTo(1);
    assertThat(db.history.findByCaseId(id)).hasSize(2);
    assertThat(db.events.assigned).containsExactly(new MaintenanceAssigned(id, equipment,
        winner.assignedTo(), reporter, db.clock.instant()));
    assertThat(db.equipmentStatus(equipment)).isEqualTo("UNDER_MAINTENANCE");
    assertThat(db.equipmentVersion(equipment)).isEqualTo(1);
  }

  @Test
  void openQueueFiltersStatusesAndOrdersOldestFirstIncludingEqualReportTimes() {
    var oldest = db.report(equipment, reporter);
    db.clock.advance();
    var tiedA = db.service.reportFault(equipment, reporter, "Tied fault A");
    var tiedB = db.service.reportFault(db.equipment(), reporter, "Tied fault B");
    var assigned = db.service.reportFault(equipment, reporter, "Assigned fault");
    db.service.assignTechnician(assigned.maintenanceCaseId(), technician, reporter);
    var expected = List.of(oldest, tiedA, tiedB).stream().sorted(queryOrder(false)).toList();
    var queue = db.service.getOpenCases();
    // A dedicated test database may contain other fixtures; only scope membership to our UUIDs.
    assertThat(queue).allMatch(item -> item.status() == MaintenanceStatus.OPEN)
        .isSortedAccordingTo(queryOrder(false));
    assertThat(queue.stream().filter(item -> db.identifiers.created.contains(item.maintenanceCaseId())).toList())
        .extracting(MaintenanceCase::maintenanceCaseId)
        .containsExactlyElementsOf(expected.stream().map(MaintenanceCase::maintenanceCaseId).toList());
    assertThatThrownBy(queue::clear).isInstanceOf(UnsupportedOperationException.class);
    queue.stream().filter(item -> item.maintenanceCaseId().equals(oldest.maintenanceCaseId()))
        .findFirst().orElseThrow().assign(technician);
    assertThat(db.service.getCase(oldest.maintenanceCaseId()).status()).isEqualTo(MaintenanceStatus.OPEN);
  }

  @Test
  void assignedQueriesSeparateTechniciansIncludeTerminalAndExcludeOpenWithAssignedUuid() {
    UUID otherTechnician = db.user("ACTIVE", "MAINTAINER");
    var older = db.report(equipment, reporter);
    db.service.assignTechnician(older.maintenanceCaseId(), technician, reporter);
    var other = db.report(equipment, reporter);
    db.service.assignTechnician(other.maintenanceCaseId(), otherTechnician, reporter);
    db.clock.advance();
    var assigned = db.service.reportFault(equipment, reporter, "Assigned");
    var inProgress = db.service.reportFault(equipment, reporter, "In progress");
    var resolved = db.service.reportFault(equipment, reporter, "Resolved");
    var unrepairable = db.service.reportFault(equipment, reporter, "Unrepairable");
    // V002 permits an assigned_to UUID on OPEN; the query must still explicitly exclude OPEN.
    UUID openId = db.identifiers.get();
    var open = MaintenanceCase.restore(new MaintenanceCaseSnapshot(openId, equipment, reporter,
        technician, null, MaintenanceStatus.OPEN, "Unassigned lifecycle", null, db.clock.instant(), null, 0));
    db.transactions.executeWithoutResult(tx -> {
      db.equipment.lockForMaintenance(equipment);
      db.cases.insert(open);
    });
    for (var item : List.of(assigned, inProgress, resolved, unrepairable)) {
      db.service.assignTechnician(item.maintenanceCaseId(), technician, reporter);
    }
    for (var item : List.of(inProgress, resolved, unrepairable)) {
      db.service.startMaintenance(item.maintenanceCaseId(), technician);
    }
    db.service.resolve(resolved.maintenanceCaseId(), "Repaired", technician);
    db.service.markUnrepairable(unrepairable.maintenanceCaseId(), "No parts", technician);
    var expected = List.of(older, assigned, inProgress, resolved, unrepairable).stream()
        .sorted(queryOrder(true)).map(MaintenanceCase::maintenanceCaseId).toList();
    var own = db.service.getAssignedCases(technician);
    assertThat(own).allMatch(item -> technician.equals(item.assignedTo()))
        .isSortedAccordingTo(queryOrder(true));
    assertThat(own).extracting(MaintenanceCase::maintenanceCaseId).containsExactlyElementsOf(expected);
    assertThat(own).extracting(MaintenanceCase::status).containsOnly(MaintenanceStatus.ASSIGNED,
        MaintenanceStatus.IN_PROGRESS, MaintenanceStatus.RESOLVED, MaintenanceStatus.UNREPAIRABLE)
        .contains(MaintenanceStatus.RESOLVED, MaintenanceStatus.UNREPAIRABLE);
    assertThat(db.service.getAssignedCases(otherTechnician)).extracting(MaintenanceCase::maintenanceCaseId)
        .containsExactly(other.maintenanceCaseId());
    assertThat(db.service.getAssignedCases(UUID.randomUUID())).isEmpty();
  }

  @Test
  void equipmentQueriesIncludeEveryStateAndOrderNewestFirstWithIdTieBreaker() {
    var oldest = db.report(equipment, reporter);
    UUID otherEquipment = db.equipment();
    var other = db.report(otherEquipment, reporter);
    db.clock.advance();
    var assigned = db.service.reportFault(equipment, reporter, "Assigned");
    var inProgress = db.service.reportFault(equipment, reporter, "In progress");
    var resolved = db.service.reportFault(equipment, reporter, "Resolved");
    var unrepairable = db.service.reportFault(equipment, reporter, "Unrepairable");
    for (var item : List.of(assigned, inProgress, resolved, unrepairable)) {
      db.service.assignTechnician(item.maintenanceCaseId(), technician, reporter);
    }
    for (var item : List.of(inProgress, resolved, unrepairable)) {
      db.service.startMaintenance(item.maintenanceCaseId(), technician);
    }
    db.service.resolve(resolved.maintenanceCaseId(), "Repaired", technician);
    db.service.markUnrepairable(unrepairable.maintenanceCaseId(), "No parts", technician);
    var expected = List.of(oldest, assigned, inProgress, resolved, unrepairable).stream()
        .sorted(queryOrder(true)).map(MaintenanceCase::maintenanceCaseId).toList();
    var cases = db.service.getEquipmentCases(equipment);
    assertThat(cases).allMatch(item -> equipment.equals(item.equipmentId())).isSortedAccordingTo(queryOrder(true));
    assertThat(cases).extracting(MaintenanceCase::maintenanceCaseId).containsExactlyElementsOf(expected);
    assertThat(cases).extracting(MaintenanceCase::status).containsExactlyInAnyOrder(MaintenanceStatus.values());
    assertThat(db.service.getEquipmentCases(otherEquipment)).extracting(MaintenanceCase::maintenanceCaseId)
        .containsExactly(other.maintenanceCaseId());
    assertThat(db.service.getEquipmentCases(UUID.randomUUID())).isEmpty();
  }

  private static Comparator<MaintenanceCase> queryOrder(boolean newestFirst) {
    var time = Comparator.comparing(MaintenanceCase::reportedAt);
    // PostgreSQL UUID ordering compares unsigned bytes; canonical strings preserve that order.
    return (newestFirst ? time.reversed() : time).thenComparing(item -> item.maintenanceCaseId().toString());
  }

  private AssignmentResult assign(UUID id, UUID target) {
    // An explicit READ_COMMITTED transaction ensures each worker has its own connection;
    // the real service joins it and the barrier is reached after its real snapshot SELECT.
    try {
      return db.transactions.execute(tx -> new AssignmentResult(db.service.assignTechnician(id, target, reporter), null));
    } catch (MaintenanceVersionConflictException conflict) {
      return new AssignmentResult(null, conflict);
    }
  }

  private record AssignmentResult(MaintenanceCase persisted, MaintenanceVersionConflictException conflict) {}
  private static final class DeliberateCompletionFailure extends RuntimeException {}

  private MaintenanceCompleted completion(MaintenanceCase terminal) {
    return new MaintenanceCompleted(terminal.maintenanceCaseId(), equipment, technician,
        technician, terminal.status(), db.clock.instant());
  }

  private void assertResolved(MaintenanceCase before, MaintenanceCase resolved) {
    assertThat(resolved.status()).isEqualTo(MaintenanceStatus.RESOLVED);
    assertThat(resolved.resolutionNote()).isEqualTo("Cable repaired");
    assertThat(resolved.resolvedAt()).isEqualTo(db.clock.instant());
    assertThat(resolved.version()).isEqualTo(before.version() + 1);
    assertPersisted(resolved);
  }

  private void assertPersisted(MaintenanceCase expected) {
    var row = db.jdbc.queryForMap("SELECT * FROM serms.maintenance_case WHERE maintenance_case_id = ?",
        expected.maintenanceCaseId());
    assertThat(row).containsEntry("maintenance_case_id", expected.maintenanceCaseId())
        .containsEntry("equipment_id", expected.equipmentId()).containsEntry("reported_by", expected.reportedBy())
        .containsEntry("assigned_to", expected.assignedTo()).containsEntry("loan_id", expected.loanId())
        .containsEntry("status", expected.status().name()).containsEntry("fault_description", expected.faultDescription())
        .containsEntry("resolution_note", expected.resolutionNote()).containsEntry("version", expected.version())
        .containsEntry("reported_at", Timestamp.from(expected.reportedAt()))
        .containsEntry("resolved_at", expected.resolvedAt() == null ? null : Timestamp.from(expected.resolvedAt()));
    assertThat(db.cases.findById(expected.maintenanceCaseId()).orElseThrow().snapshot()).isEqualTo(expected.snapshot());
  }
}
