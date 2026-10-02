package sg.edu.nus.serms.maintenance.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class MaintenanceCaseSnapshotTest {
  private static final UUID ID = new UUID(0, 1);
  private static final UUID TECHNICIAN = new UUID(0, 4);
  private static final Instant NOW = Instant.parse("2026-10-02T02:00:00Z");

  private static MaintenanceCase caseAt(MaintenanceStatus status) {
    var item = MaintenanceCase.restore(new MaintenanceCaseSnapshot(ID, new UUID(0, 2),
        new UUID(0, 3), null, new UUID(0, 5), MaintenanceStatus.OPEN, "Fault", null, NOW, null, 9));
    if (status == MaintenanceStatus.OPEN) return item;
    item.assign(TECHNICIAN);
    if (status == MaintenanceStatus.ASSIGNED) return item;
    item.start();
    if (status == MaintenanceStatus.RESOLVED) item.resolve("Repaired", NOW.plusSeconds(10));
    if (status == MaintenanceStatus.UNREPAIRABLE) item.markUnrepairable("No parts", NOW.plusSeconds(10));
    return item;
  }

  @ParameterizedTest
  @EnumSource(MaintenanceStatus.class)
  void roundTripsEveryStateWithVersionAndLoan(MaintenanceStatus status) {
    var original = caseAt(status);
    var snapshot = original.snapshot();
    var restored = MaintenanceCase.restore(snapshot);
    assertThat(restored).isNotSameAs(original);
    assertThat(restored.snapshot()).isEqualTo(snapshot);
    assertThat(restored.status()).isEqualTo(status);
    assertThat(restored.version()).isEqualTo(9);
    assertThat(restored.loanId()).isEqualTo(new UUID(0, 5));
    assertThat(restored.reportedBy()).isEqualTo(new UUID(0, 3));
    if (status == MaintenanceStatus.OPEN) {
      restored.assign(TECHNICIAN);
      assertThat(original.status()).isEqualTo(MaintenanceStatus.OPEN);
      assertThat(snapshot.assignedTo()).isNull();
    }
  }

  static Stream<Arguments> invalidSnapshots() {
    return Stream.of(
        Arguments.of(MaintenanceStatus.OPEN, "resolvedAt", NOW),
        Arguments.of(MaintenanceStatus.OPEN, "resolutionNote", "Premature"),
        Arguments.of(MaintenanceStatus.ASSIGNED, "assignedTo", null),
        Arguments.of(MaintenanceStatus.IN_PROGRESS, "assignedTo", null),
        Arguments.of(MaintenanceStatus.RESOLVED, "assignedTo", null),
        Arguments.of(MaintenanceStatus.UNREPAIRABLE, "assignedTo", null),
        Arguments.of(MaintenanceStatus.ASSIGNED, "resolvedAt", NOW),
        Arguments.of(MaintenanceStatus.IN_PROGRESS, "resolutionNote", "Premature"),
        Arguments.of(MaintenanceStatus.RESOLVED, "resolvedAt", null),
        Arguments.of(MaintenanceStatus.UNREPAIRABLE, "resolvedAt", NOW.minusSeconds(1)),
        Arguments.of(MaintenanceStatus.RESOLVED, "resolutionNote", " "),
        Arguments.of(MaintenanceStatus.UNREPAIRABLE, "resolutionNote", null),
        Arguments.of(MaintenanceStatus.OPEN, "version", -1),
        Arguments.of(MaintenanceStatus.OPEN, "faultDescription", " "));
  }

  @ParameterizedTest
  @MethodSource("invalidSnapshots")
  void rejectsInvalidDurableState(MaintenanceStatus status, String field, Object value) {
    var overrides = new HashMap<String, Object>();
    overrides.put(field, value);
    assertThatIllegalArgumentException().isThrownBy(() -> changed(caseAt(status).snapshot(), overrides));
  }

  @Test
  void acceptsOptionalAssignmentOnOpenAndIndependentFault() {
    var s = new MaintenanceCaseSnapshot(ID, ID, ID, TECHNICIAN, null,
        MaintenanceStatus.OPEN, "Fault", null, NOW, null, 0);
    var restored = MaintenanceCase.restore(s);
    assertThat(restored.assignedTo()).isEqualTo(TECHNICIAN);
    assertThat(restored.loanId()).isNull();
    assertThat(restored.snapshot()).isEqualTo(s);
  }

  @Test
  void restoredInProgressCaseCanCompleteAtReportTime() {
    var restored = MaintenanceCase.restore(caseAt(MaintenanceStatus.IN_PROGRESS).snapshot());
    restored.resolve("Repaired", NOW);
    assertThat(restored.resolvedAt()).isEqualTo(NOW);
    assertThat(restored.status()).isEqualTo(MaintenanceStatus.RESOLVED);
  }

  @Test
  void rejectsNullSnapshotAndRequiredIdentifiers() {
    assertThatNullPointerException().isThrownBy(() -> MaintenanceCase.restore(null));
    assertThatNullPointerException().isThrownBy(() -> new MaintenanceCaseSnapshot(null, ID, ID,
        null, null, MaintenanceStatus.OPEN, "Fault", null, NOW, null, 0));
    assertThatNullPointerException().isThrownBy(() -> new MaintenanceCaseSnapshot(ID, null, ID,
        null, null, MaintenanceStatus.OPEN, "Fault", null, NOW, null, 0));
    assertThatNullPointerException().isThrownBy(() -> new MaintenanceCaseSnapshot(ID, ID, null,
        null, null, MaintenanceStatus.OPEN, "Fault", null, NOW, null, 0));
    assertThatNullPointerException().isThrownBy(() -> new MaintenanceCaseSnapshot(ID, ID, ID,
        null, null, null, "Fault", null, NOW, null, 0));
    assertThatNullPointerException().isThrownBy(() -> new MaintenanceCaseSnapshot(ID, ID, ID,
        null, null, MaintenanceStatus.OPEN, "Fault", null, null, null, 0));
  }

  private static MaintenanceCaseSnapshot changed(MaintenanceCaseSnapshot s, Map<String, Object> values) {
    return new MaintenanceCaseSnapshot(s.maintenanceCaseId(), s.equipmentId(), s.reportedBy(),
        (UUID) values.getOrDefault("assignedTo", s.assignedTo()), s.loanId(), s.status(),
        (String) values.getOrDefault("faultDescription", s.faultDescription()),
        (String) values.getOrDefault("resolutionNote", s.resolutionNote()), s.reportedAt(),
        (Instant) values.getOrDefault("resolvedAt", s.resolvedAt()),
        (Integer) values.getOrDefault("version", s.version()));
  }
}
