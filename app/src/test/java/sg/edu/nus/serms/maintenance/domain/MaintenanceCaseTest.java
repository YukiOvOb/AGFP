package sg.edu.nus.serms.maintenance.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MaintenanceCaseTest {
  private static final UUID CASE_ID = new UUID(0, 1);
  private static final UUID REPORT_ID = new UUID(0, 2);
  private static final UUID EQUIPMENT_ID = new UUID(0, 3);
  private static final UUID TECHNICIAN_ID = new UUID(0, 4);
  private static final Instant CREATED = Instant.parse("2026-09-29T08:00:00Z");
  private static final Instant ASSIGNED = CREATED.plusSeconds(60);
  private static final Instant STARTED = ASSIGNED.plusSeconds(60);
  private static final Instant COMPLETED = STARTED.plusSeconds(60);
  private static final Instant CLOSED = COMPLETED.plusSeconds(60);

  private MaintenanceCase newCase() {
    return new MaintenanceCase(CASE_ID, REPORT_ID, EQUIPMENT_ID, CREATED);
  }

  private MaintenanceCase startedCase() {
    MaintenanceCase maintenanceCase = newCase();
    maintenanceCase.assign(TECHNICIAN_ID, ASSIGNED);
    maintenanceCase.start(STARTED);
    return maintenanceCase;
  }

  @Test
  void newCaseRetainsRelationshipsAndStartsReported() {
    MaintenanceCase maintenanceCase = newCase();

    assertThat(maintenanceCase.maintenanceCaseId()).isEqualTo(CASE_ID);
    assertThat(maintenanceCase.faultReportId()).isEqualTo(REPORT_ID);
    assertThat(maintenanceCase.equipmentId()).isEqualTo(EQUIPMENT_ID);
    assertThat(maintenanceCase.createdAt()).isEqualTo(CREATED);
    assertThat(maintenanceCase.status()).isEqualTo(MaintenanceStatus.REPORTED);
    assertThat(maintenanceCase.assignedTechnicianId()).isNull();
    assertThat(maintenanceCase.completedAt()).isNull();
  }

  @Test
  void assignmentAndStartRetainTechnicianAndTimes() {
    MaintenanceCase maintenanceCase = newCase();

    maintenanceCase.assign(TECHNICIAN_ID, ASSIGNED);
    assertThat(maintenanceCase.status()).isEqualTo(MaintenanceStatus.ASSIGNED);
    assertThat(maintenanceCase.assignedTechnicianId()).isEqualTo(TECHNICIAN_ID);
    assertThat(maintenanceCase.assignedAt()).isEqualTo(ASSIGNED);

    maintenanceCase.start(STARTED);
    assertThat(maintenanceCase.status()).isEqualTo(MaintenanceStatus.IN_PROGRESS);
    assertThat(maintenanceCase.startedAt()).isEqualTo(STARTED);
  }

  @Test
  void resolvedWorkflowRetainsWorkAndCompletionData() {
    MaintenanceCase maintenanceCase = startedCase();
    maintenanceCase.recordDiagnosis("  Broken cable  ");
    maintenanceCase.recordRepairAction("  Replaced cable  ");
    maintenanceCase.recordMaintenanceNotes("  Inspected connector  ");
    maintenanceCase.recordMaintenanceNotes("Passed final check");

    maintenanceCase.resolve("  Tested and working  ", COMPLETED);
    assertThat(maintenanceCase.status()).isEqualTo(MaintenanceStatus.RESOLVED);
    assertThat(maintenanceCase.diagnosis()).isEqualTo("Broken cable");
    assertThat(maintenanceCase.repairAction()).isEqualTo("Replaced cable");
    assertThat(maintenanceCase.maintenanceNotes())
        .containsExactly("Inspected connector", "Passed final check");
    assertThat(maintenanceCase.completionInformation()).isEqualTo("Tested and working");
    assertThat(maintenanceCase.completedAt()).isEqualTo(COMPLETED);

    maintenanceCase.close(CLOSED);
    assertThat(maintenanceCase.status()).isEqualTo(MaintenanceStatus.CLOSED);
    assertThat(maintenanceCase.closedAt()).isEqualTo(CLOSED);
  }

  @Test
  void notRepairableWorkflowRetainsOutcomeUntilClosed() {
    MaintenanceCase maintenanceCase = startedCase();

    maintenanceCase.markNotRepairable("Parts unavailable", COMPLETED);
    assertThat(maintenanceCase.status()).isEqualTo(MaintenanceStatus.NOT_REPAIRABLE);
    assertThat(maintenanceCase.completionInformation()).isEqualTo("Parts unavailable");
    assertThat(maintenanceCase.completedAt()).isEqualTo(COMPLETED);
    maintenanceCase.close(CLOSED);
    assertThat(maintenanceCase.status()).isEqualTo(MaintenanceStatus.CLOSED);
    assertThat(maintenanceCase.closedAt()).isEqualTo(CLOSED);
  }

  @Test
  void illegalLifecycleActionUsesTheExistingStateMachineException() {
    MaintenanceCase maintenanceCase = startedCase();

    assertThatThrownBy(() -> maintenanceCase.close(COMPLETED))
        .isInstanceOf(InvalidMaintenanceTransitionException.class)
        .satisfies(
            failure -> {
              var transition = (InvalidMaintenanceTransitionException) failure;
              assertThat(transition.getCurrentStatus()).isEqualTo(MaintenanceStatus.IN_PROGRESS);
              assertThat(transition.getAttemptedAction()).isEqualTo(MaintenanceAction.CLOSE);
            });
    assertThat(maintenanceCase.status()).isEqualTo(MaintenanceStatus.IN_PROGRESS);
    assertThat(maintenanceCase.closedAt()).isNull();
  }

  @Test
  void closedCaseIsTerminalAndRejectsFurtherWork() {
    MaintenanceCase maintenanceCase = startedCase();
    maintenanceCase.resolve("Working", COMPLETED);
    maintenanceCase.close(CLOSED);

    assertThatThrownBy(() -> maintenanceCase.close(CLOSED.plusSeconds(1)))
        .isInstanceOf(InvalidMaintenanceTransitionException.class);
    assertThatThrownBy(() -> maintenanceCase.resolve("Again", CLOSED.plusSeconds(1)))
        .isInstanceOf(InvalidMaintenanceTransitionException.class);
    assertThatIllegalStateException().isThrownBy(() -> maintenanceCase.recordDiagnosis("Later"));
    assertThatIllegalStateException().isThrownBy(() -> maintenanceCase.recordRepairAction("Later"));
    assertThatIllegalStateException().isThrownBy(() -> maintenanceCase.recordMaintenanceNotes("Later"));
    assertThat(maintenanceCase.status()).isEqualTo(MaintenanceStatus.CLOSED);
    assertThat(maintenanceCase.completionInformation()).isEqualTo("Working");
  }

  @Test
  void workInformationIsOnlyEditableInProgressAndNotesAreAppendOnly() {
    MaintenanceCase reported = newCase();
    assertThatIllegalStateException().isThrownBy(() -> reported.recordDiagnosis("Fault"));
    assertThatIllegalStateException().isThrownBy(() -> reported.recordRepairAction("Repair"));
    assertThatIllegalStateException().isThrownBy(() -> reported.recordMaintenanceNotes("Note"));

    MaintenanceCase maintenanceCase = startedCase();
    maintenanceCase.recordMaintenanceNotes("First note");
    var snapshot = maintenanceCase.maintenanceNotes();
    maintenanceCase.recordMaintenanceNotes("Second note");
    assertThat(snapshot).containsExactly("First note");
    assertThat(maintenanceCase.maintenanceNotes()).containsExactly("First note", "Second note");
    assertThatThrownBy(() -> snapshot.add("External"))
        .isInstanceOf(UnsupportedOperationException.class);
    maintenanceCase.resolve("Working", COMPLETED);
    assertThatIllegalStateException().isThrownBy(() -> maintenanceCase.recordMaintenanceNotes("Late"));
  }

  @Test
  void blankWorkAndCompletionInformationDoNotCausePartialMutation() {
    MaintenanceCase maintenanceCase = startedCase();
    maintenanceCase.recordDiagnosis("Cable fault");

    assertThatIllegalArgumentException().isThrownBy(() -> maintenanceCase.recordDiagnosis(" \t "));
    assertThatIllegalArgumentException().isThrownBy(() -> maintenanceCase.recordRepairAction(null));
    assertThatIllegalArgumentException().isThrownBy(() -> maintenanceCase.recordMaintenanceNotes(""));
    assertThatIllegalArgumentException().isThrownBy(() -> maintenanceCase.resolve(" \n ", COMPLETED));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> maintenanceCase.markNotRepairable(null, COMPLETED));
    assertThat(maintenanceCase.diagnosis()).isEqualTo("Cable fault");
    assertThat(maintenanceCase.repairAction()).isNull();
    assertThat(maintenanceCase.maintenanceNotes()).isEmpty();
    assertThat(maintenanceCase.status()).isEqualTo(MaintenanceStatus.IN_PROGRESS);
    assertThat(maintenanceCase.completedAt()).isNull();
  }

  @Test
  void invalidIdentifiersAndTimesDoNotPartiallyChangeCase() {
    assertThatNullPointerException()
        .isThrownBy(() -> new MaintenanceCase(null, REPORT_ID, EQUIPMENT_ID, CREATED));
    MaintenanceCase maintenanceCase = newCase();
    assertThatNullPointerException().isThrownBy(() -> maintenanceCase.assign(null, ASSIGNED));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> maintenanceCase.assign(TECHNICIAN_ID, CREATED.minusSeconds(1)));
    assertThat(maintenanceCase.status()).isEqualTo(MaintenanceStatus.REPORTED);
    assertThat(maintenanceCase.assignedAt()).isNull();

    maintenanceCase.assign(TECHNICIAN_ID, ASSIGNED);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> maintenanceCase.start(ASSIGNED.minusSeconds(1)));
    assertThat(maintenanceCase.startedAt()).isNull();
    maintenanceCase.start(STARTED);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> maintenanceCase.resolve("Working", STARTED.minusSeconds(1)));
    assertThat(maintenanceCase.status()).isEqualTo(MaintenanceStatus.IN_PROGRESS);
    maintenanceCase.resolve("Working", COMPLETED);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> maintenanceCase.close(COMPLETED.minusSeconds(1)));
    assertThat(maintenanceCase.closedAt()).isNull();
  }

  @Test
  void copyDoesNotShareMutableCaseState() {
    MaintenanceCase original = startedCase();
    original.recordMaintenanceNotes("Original");
    MaintenanceCase copy = original.copy();
    copy.recordMaintenanceNotes("Copy only");
    copy.resolve("Done", COMPLETED);

    assertThat(original.status()).isEqualTo(MaintenanceStatus.IN_PROGRESS);
    assertThat(original.maintenanceNotes()).containsExactly("Original");
    assertThat(copy.maintenanceNotes()).containsExactly("Original", "Copy only");
  }
}
