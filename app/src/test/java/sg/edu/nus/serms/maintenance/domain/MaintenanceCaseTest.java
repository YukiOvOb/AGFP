package sg.edu.nus.serms.maintenance.domain;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MaintenanceCaseTest {
  private static MaintenanceCase startedCase() {
    MaintenanceCase maintenanceCase = new MaintenanceCase();
    maintenanceCase.assign();
    maintenanceCase.start();
    return maintenanceCase;
  }

  @Test
  void newCaseStartsReportedAndHasNoWorkInformation() {
    MaintenanceCase maintenanceCase = new MaintenanceCase();

    assertThat(maintenanceCase.status()).isEqualTo(MaintenanceStatus.REPORTED);
    assertThat(maintenanceCase.diagnosis()).isNull();
    assertThat(maintenanceCase.repairAction()).isNull();
    assertThat(maintenanceCase.maintenanceNotes()).isEmpty();
    assertThat(maintenanceCase.completionInformation()).isNull();
  }

  @Test
  void assignmentAndStartUseTheApprovedLifecycle() {
    MaintenanceCase maintenanceCase = new MaintenanceCase();

    maintenanceCase.assign();
    assertThat(maintenanceCase.status()).isEqualTo(MaintenanceStatus.ASSIGNED);
    maintenanceCase.start();
    assertThat(maintenanceCase.status()).isEqualTo(MaintenanceStatus.IN_PROGRESS);
  }

  @Test
  void resolvedCaseRetainsWorkAndCompletionInformationUntilClosed() {
    MaintenanceCase maintenanceCase = startedCase();
    maintenanceCase.recordDiagnosis("  Broken cable  ");
    maintenanceCase.recordRepairAction("  Replaced cable  ");
    maintenanceCase.recordMaintenanceNotes("  Inspected connector  ");
    maintenanceCase.recordMaintenanceNotes("Passed final check");

    maintenanceCase.resolve("  Tested and working  ");
    assertThat(maintenanceCase.status()).isEqualTo(MaintenanceStatus.RESOLVED);
    assertThat(maintenanceCase.diagnosis()).isEqualTo("Broken cable");
    assertThat(maintenanceCase.repairAction()).isEqualTo("Replaced cable");
    assertThat(maintenanceCase.maintenanceNotes())
        .containsExactly("Inspected connector", "Passed final check");
    assertThat(maintenanceCase.completionInformation()).isEqualTo("Tested and working");

    maintenanceCase.close();
    assertThat(maintenanceCase.status()).isEqualTo(MaintenanceStatus.CLOSED);
    assertThat(maintenanceCase.completionInformation()).isEqualTo("Tested and working");
  }

  @Test
  void notRepairableCaseRetainsCompletionInformationUntilClosed() {
    MaintenanceCase maintenanceCase = startedCase();

    maintenanceCase.markNotRepairable("Parts unavailable");
    assertThat(maintenanceCase.status()).isEqualTo(MaintenanceStatus.NOT_REPAIRABLE);
    assertThat(maintenanceCase.completionInformation()).isEqualTo("Parts unavailable");
    maintenanceCase.close();
    assertThat(maintenanceCase.status()).isEqualTo(MaintenanceStatus.CLOSED);
    assertThat(maintenanceCase.completionInformation()).isEqualTo("Parts unavailable");
  }

  @Test
  void illegalLifecycleActionUsesStateMachineExceptionAndLeavesCaseUnchanged() {
    MaintenanceCase maintenanceCase = startedCase();

    assertThatThrownBy(maintenanceCase::close)
        .isInstanceOf(InvalidMaintenanceTransitionException.class)
        .satisfies(
            failure -> {
              var transition = (InvalidMaintenanceTransitionException) failure;
              assertThat(transition.getCurrentStatus()).isEqualTo(MaintenanceStatus.IN_PROGRESS);
              assertThat(transition.getAttemptedAction()).isEqualTo(MaintenanceAction.CLOSE);
            });
    assertThat(maintenanceCase.status()).isEqualTo(MaintenanceStatus.IN_PROGRESS);
    assertThat(maintenanceCase.completionInformation()).isNull();
  }

  static Stream<Arguments> closedLifecycleActions() {
    return Stream.of(
        Arguments.of(MaintenanceAction.ASSIGN, (Consumer<MaintenanceCase>) MaintenanceCase::assign),
        Arguments.of(MaintenanceAction.START, (Consumer<MaintenanceCase>) MaintenanceCase::start),
        Arguments.of(
            MaintenanceAction.RESOLVE,
            (Consumer<MaintenanceCase>) maintenanceCase -> maintenanceCase.resolve("Done")),
        Arguments.of(
            MaintenanceAction.MARK_NOT_REPAIRABLE,
            (Consumer<MaintenanceCase>) maintenanceCase -> maintenanceCase.markNotRepairable("Done")),
        Arguments.of(MaintenanceAction.CLOSE, (Consumer<MaintenanceCase>) MaintenanceCase::close));
  }

  @ParameterizedTest
  @MethodSource("closedLifecycleActions")
  void closedCaseRejectsEveryLifecycleAction(
      MaintenanceAction action, Consumer<MaintenanceCase> operation) {
    MaintenanceCase maintenanceCase = startedCase();
    maintenanceCase.resolve("Working");
    maintenanceCase.close();

    assertThatThrownBy(() -> operation.accept(maintenanceCase))
        .isInstanceOf(InvalidMaintenanceTransitionException.class)
        .satisfies(
            failure -> {
              var transition = (InvalidMaintenanceTransitionException) failure;
              assertThat(transition.getCurrentStatus()).isEqualTo(MaintenanceStatus.CLOSED);
              assertThat(transition.getAttemptedAction()).isEqualTo(action);
            });
    assertThat(maintenanceCase.status()).isEqualTo(MaintenanceStatus.CLOSED);
    assertThat(maintenanceCase.completionInformation()).isEqualTo("Working");
  }

  @Test
  void workInformationCanOnlyBeRecordedInProgress() {
    MaintenanceCase reported = new MaintenanceCase();
    assertThatIllegalStateException().isThrownBy(() -> reported.recordDiagnosis("Fault"));
    assertThatIllegalStateException().isThrownBy(() -> reported.recordRepairAction("Repair"));
    assertThatIllegalStateException().isThrownBy(() -> reported.recordMaintenanceNotes("Note"));

    MaintenanceCase completed = startedCase();
    completed.resolve("Working");
    assertThatIllegalStateException().isThrownBy(() -> completed.recordDiagnosis("Later fault"));
    assertThatIllegalStateException().isThrownBy(() -> completed.recordRepairAction("Later repair"));
    assertThatIllegalStateException().isThrownBy(() -> completed.recordMaintenanceNotes("Later note"));
    completed.close();
    assertThatIllegalStateException().isThrownBy(() -> completed.recordMaintenanceNotes("Closed note"));
    assertThat(completed.maintenanceNotes()).isEmpty();
  }

  @Test
  void notesCannotBeChangedThroughReturnedList() {
    MaintenanceCase maintenanceCase = startedCase();
    maintenanceCase.recordMaintenanceNotes("First note");
    List<String> notes = maintenanceCase.maintenanceNotes();

    assertThatThrownBy(() -> notes.add("External change"))
        .isInstanceOf(UnsupportedOperationException.class);
    maintenanceCase.recordMaintenanceNotes("Second note");
    assertThat(notes).containsExactly("First note");
    assertThat(maintenanceCase.maintenanceNotes()).containsExactly("First note", "Second note");
  }

  @Test
  void blankOrNullWorkInformationIsRejectedWithoutChangingRecordedValues() {
    MaintenanceCase maintenanceCase = startedCase();
    maintenanceCase.recordDiagnosis("Cable fault");
    maintenanceCase.recordRepairAction("Cable replaced");
    maintenanceCase.recordMaintenanceNotes("Inspected");

    assertThatIllegalArgumentException().isThrownBy(() -> maintenanceCase.recordDiagnosis(" \t "));
    assertThatIllegalArgumentException().isThrownBy(() -> maintenanceCase.recordRepairAction(null));
    assertThatIllegalArgumentException().isThrownBy(() -> maintenanceCase.recordMaintenanceNotes(""));
    assertThat(maintenanceCase.diagnosis()).isEqualTo("Cable fault");
    assertThat(maintenanceCase.repairAction()).isEqualTo("Cable replaced");
    assertThat(maintenanceCase.maintenanceNotes()).containsExactly("Inspected");
  }

  @Test
  void completionInformationIsRequiredAndDoesNotCausePartialTransition() {
    MaintenanceCase maintenanceCase = startedCase();

    assertThatIllegalArgumentException().isThrownBy(() -> maintenanceCase.resolve(" \n "));
    assertThatIllegalArgumentException().isThrownBy(() -> maintenanceCase.markNotRepairable(null));
    assertThat(maintenanceCase.status()).isEqualTo(MaintenanceStatus.IN_PROGRESS);
    assertThat(maintenanceCase.completionInformation()).isNull();
  }
}
