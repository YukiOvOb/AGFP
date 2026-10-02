package sg.edu.nus.serms.maintenance.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MaintenanceCaseSnapshotTest {
  private static final Instant CREATED = Instant.parse("2026-10-02T01:00:00Z");
  private static final UUID TECHNICIAN = new UUID(0, 4);

  static Stream<Arguments> lifecycles() {
    return Stream.of(
        Arguments.of(MaintenanceStatus.REPORTED, false),
        Arguments.of(MaintenanceStatus.ASSIGNED, false),
        Arguments.of(MaintenanceStatus.IN_PROGRESS, false),
        Arguments.of(MaintenanceStatus.RESOLVED, false),
        Arguments.of(MaintenanceStatus.NOT_REPAIRABLE, true),
        Arguments.of(MaintenanceStatus.CLOSED, false),
        Arguments.of(MaintenanceStatus.CLOSED, true));
  }

  private static MaintenanceCase caseAt(MaintenanceStatus status, boolean notRepairable) {
    var item = new MaintenanceCase(new UUID(0, 1), new UUID(0, 2), new UUID(0, 3), CREATED);
    if (status == MaintenanceStatus.REPORTED) return item;
    item.assign(TECHNICIAN, CREATED.plusSeconds(1));
    if (status == MaintenanceStatus.ASSIGNED) return item;
    item.start(CREATED.plusSeconds(2));
    item.recordDiagnosis("Broken cable");
    item.recordRepairAction("Inspected cable");
    item.recordMaintenanceNotes("First note");
    item.recordMaintenanceNotes("Second note");
    if (status == MaintenanceStatus.IN_PROGRESS) return item;
    if (notRepairable) item.markNotRepairable("No replacement", CREATED.plusSeconds(3));
    else item.resolve("Cable replaced", CREATED.plusSeconds(3));
    if (status == MaintenanceStatus.CLOSED) item.close(CREATED.plusSeconds(4));
    return item;
  }

  @ParameterizedTest
  @MethodSource("lifecycles")
  void faithfullyRestoresEveryLifecycle(MaintenanceStatus status, boolean notRepairable) {
    var original = caseAt(status, notRepairable);
    var snapshot = original.snapshot();
    var restored = MaintenanceCase.restore(snapshot);
    assertThat(restored).isNotSameAs(original);
    assertThat(restored.snapshot()).isEqualTo(snapshot);
    assertThat(restored.status()).isEqualTo(status);
    assertThat(restored.completionOutcome()).isEqualTo(original.completionOutcome());
    if (status == MaintenanceStatus.IN_PROGRESS) {
      restored.recordMaintenanceNotes("Restored only");
      assertThat(original.maintenanceNotes()).containsExactly("First note", "Second note");
      assertThat(snapshot.maintenanceNotes()).containsExactly("First note", "Second note");
    }
    if (status == MaintenanceStatus.RESOLVED || status == MaintenanceStatus.NOT_REPAIRABLE) {
      restored.close(CREATED.plusSeconds(5));
      assertThat(restored.completionOutcome()).isEqualTo(snapshot.completionOutcome());
      assertThat(original.status()).isEqualTo(status);
    }
    if (status == MaintenanceStatus.CLOSED) {
      assertThatThrownBy(() -> restored.start(CREATED.plusSeconds(5)))
          .isInstanceOf(InvalidMaintenanceTransitionException.class);
    }
  }

  static Stream<Arguments> impossibleSnapshots() {
    return Stream.of(
        Arguments.of(MaintenanceStatus.CLOSED, "completionOutcome", null),
        Arguments.of(MaintenanceStatus.CLOSED, "completionOutcome", MaintenanceStatus.CLOSED),
        Arguments.of(MaintenanceStatus.RESOLVED, "completionOutcome", MaintenanceStatus.NOT_REPAIRABLE),
        Arguments.of(MaintenanceStatus.NOT_REPAIRABLE, "completionOutcome", MaintenanceStatus.RESOLVED),
        Arguments.of(MaintenanceStatus.ASSIGNED, "technician", null),
        Arguments.of(MaintenanceStatus.ASSIGNED, "assignedAt", null),
        Arguments.of(MaintenanceStatus.IN_PROGRESS, "startedAt", null),
        Arguments.of(MaintenanceStatus.RESOLVED, "completedAt", null),
        Arguments.of(MaintenanceStatus.CLOSED, "closedAt", null),
        Arguments.of(MaintenanceStatus.CLOSED, "completionInformation", " "),
        Arguments.of(MaintenanceStatus.REPORTED, "assignedAt", CREATED),
        Arguments.of(MaintenanceStatus.ASSIGNED, "startedAt", CREATED),
        Arguments.of(MaintenanceStatus.IN_PROGRESS, "closedAt", CREATED),
        Arguments.of(MaintenanceStatus.IN_PROGRESS, "completionOutcome", MaintenanceStatus.RESOLVED),
        Arguments.of(MaintenanceStatus.REPORTED, "diagnosis", "Premature"),
        Arguments.of(MaintenanceStatus.IN_PROGRESS, "notes", List.of(" ")),
        Arguments.of(MaintenanceStatus.CLOSED, "assignedAt", CREATED.minusSeconds(1)),
        Arguments.of(MaintenanceStatus.CLOSED, "startedAt", CREATED),
        Arguments.of(MaintenanceStatus.CLOSED, "completedAt", CREATED.plusSeconds(1)),
        Arguments.of(MaintenanceStatus.CLOSED, "closedAt", CREATED.plusSeconds(2)));
  }

  @ParameterizedTest
  @MethodSource("impossibleSnapshots")
  void rejectsImpossibleStateBeforeRestoration(MaintenanceStatus status, String field, Object value) {
    var source = caseAt(status, status == MaintenanceStatus.NOT_REPAIRABLE).snapshot();
    var overrides = new java.util.HashMap<String, Object>();
    overrides.put(field, value);
    assertThatIllegalArgumentException().isThrownBy(() -> changed(source, overrides));
  }

  @Test
  void snapshotDefensivelyCopiesNotesAndExposesAnImmutableList() {
    var source = caseAt(MaintenanceStatus.IN_PROGRESS, false).snapshot();
    var notes = new ArrayList<>(List.of("Retained"));
    var snapshot = changed(source, Map.of("notes", notes));
    notes.add("External");
    assertThat(snapshot.maintenanceNotes()).containsExactly("Retained");
    assertThatThrownBy(() -> snapshot.maintenanceNotes().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void restoredReportedCaseCanContinueNormalLifecycle() {
    var restored = MaintenanceCase.restore(caseAt(MaintenanceStatus.REPORTED, false).snapshot());
    restored.assign(TECHNICIAN, CREATED);
    restored.start(CREATED);
    restored.resolve("Repaired", CREATED);
    restored.close(CREATED);
    assertThat(restored.status()).isEqualTo(MaintenanceStatus.CLOSED);
    assertThat(restored.completionOutcome()).isEqualTo(MaintenanceStatus.RESOLVED);
  }

  @Test
  void rejectsNullSnapshot() {
    assertThatNullPointerException().isThrownBy(() -> MaintenanceCase.restore(null));
  }

  @SuppressWarnings("unchecked")
  private static MaintenanceCaseSnapshot changed(MaintenanceCaseSnapshot s, Map<String, Object> values) {
    return new MaintenanceCaseSnapshot(s.maintenanceCaseId(), s.faultReportId(), s.equipmentId(),
        s.status(), (MaintenanceStatus) values.getOrDefault("completionOutcome", s.completionOutcome()),
        (UUID) values.getOrDefault("technician", s.assignedTechnicianId()), s.createdAt(),
        (Instant) values.getOrDefault("assignedAt", s.assignedAt()),
        (Instant) values.getOrDefault("startedAt", s.startedAt()),
        (Instant) values.getOrDefault("completedAt", s.completedAt()),
        (Instant) values.getOrDefault("closedAt", s.closedAt()),
        (String) values.getOrDefault("diagnosis", s.diagnosis()), s.repairAction(),
        (List<String>) values.getOrDefault("notes", s.maintenanceNotes()),
        (String) values.getOrDefault("completionInformation", s.completionInformation()));
  }
}
