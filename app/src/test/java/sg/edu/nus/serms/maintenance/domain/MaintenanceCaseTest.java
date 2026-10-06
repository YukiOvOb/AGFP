package sg.edu.nus.serms.maintenance.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MaintenanceCaseTest {
  private static final UUID ID = new UUID(0, 1);
  private static final UUID EQUIPMENT = new UUID(0, 2);
  private static final UUID REPORTER = new UUID(0, 3);
  private static final UUID TECHNICIAN = new UUID(0, 4);
  private static final UUID LOAN = new UUID(0, 5);
  private static final Instant NOW = Instant.parse("2026-10-02T02:00:00Z");

  private MaintenanceCase newCase(UUID loanId) {
    return new MaintenanceCase(ID, EQUIPMENT, REPORTER, loanId, "  Broken cable  ", NOW);
  }

  private MaintenanceCase started() {
    var item = newCase(null);
    item.assign(TECHNICIAN);
    item.start();
    return item;
  }

  @Test
  void startsOpenWithOnlyDurableFaultInformation() {
    var item = newCase(null);
    assertThat(item.maintenanceCaseId()).isEqualTo(ID);
    assertThat(item.equipmentId()).isEqualTo(EQUIPMENT);
    assertThat(item.reportedBy()).isEqualTo(REPORTER);
    assertThat(item.faultDescription()).isEqualTo("Broken cable");
    assertThat(item.reportedAt()).isEqualTo(NOW);
    assertThat(item.status()).isEqualTo(MaintenanceStatus.OPEN);
    assertThat(item.assignedTo()).isNull();
    assertThat(item.loanId()).isNull();
    assertThat(item.resolutionNote()).isNull();
    assertThat(item.resolvedAt()).isNull();
    assertThat(item.version()).isZero();
  }

  @Test
  void retainsOptionalLoanAndAssignment() {
    var item = newCase(LOAN);
    item.assign(TECHNICIAN);
    assertThat(item.loanId()).isEqualTo(LOAN);
    assertThat(item.assignedTo()).isEqualTo(TECHNICIAN);
    assertThat(item.status()).isEqualTo(MaintenanceStatus.ASSIGNED);
    item.start();
    assertThat(item.status()).isEqualTo(MaintenanceStatus.IN_PROGRESS);
  }

  @ParameterizedTest
  @EnumSource(value = MaintenanceStatus.class, names = {"RESOLVED", "UNREPAIRABLE"})
  void terminalPathsRetainResultNoteAndTime(MaintenanceStatus result) {
    var item = started();
    if (result == MaintenanceStatus.RESOLVED) item.resolve("  Repaired  ", NOW.plusSeconds(30));
    else item.markUnrepairable("  No parts  ", NOW.plusSeconds(30));
    assertThat(item.status()).isEqualTo(result);
    assertThat(item.resolutionNote()).isEqualTo(result == MaintenanceStatus.RESOLVED ? "Repaired" : "No parts");
    assertThat(item.resolvedAt()).isEqualTo(NOW.plusSeconds(30));
    assertThat(item.copy().snapshot()).isEqualTo(item.snapshot());
    assertThatThrownBy(() -> item.start()).isInstanceOf(InvalidMaintenanceTransitionException.class);
    assertThatThrownBy(() -> item.assign(TECHNICIAN)).isInstanceOf(InvalidMaintenanceTransitionException.class);
    assertThatThrownBy(() -> item.resolve("Again", NOW)).isInstanceOf(InvalidMaintenanceTransitionException.class);
    assertThatThrownBy(() -> item.markUnrepairable("Again", NOW)).isInstanceOf(InvalidMaintenanceTransitionException.class);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void rejectsInvalidFaultDescription(String description) {
    assertThatIllegalArgumentException().isThrownBy(() ->
        new MaintenanceCase(ID, EQUIPMENT, REPORTER, null, description, NOW));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void invalidResolutionNoteLeavesAggregateUnchanged(String note) {
    var item = started();
    var before = item.snapshot();
    assertThatIllegalArgumentException().isThrownBy(() -> item.resolve(note, NOW));
    assertThatIllegalArgumentException().isThrownBy(() -> item.markUnrepairable(note, NOW));
    assertThat(item.snapshot()).isEqualTo(before);
  }

  @Test
  void invalidTimeOrTechnicianDoesNotPartiallyMutate() {
    var item = newCase(null);
    assertThatNullPointerException().isThrownBy(() -> item.assign(null));
    assertThat(item.status()).isEqualTo(MaintenanceStatus.OPEN);
    item.assign(TECHNICIAN);
    item.start();
    var before = item.snapshot();
    assertThatIllegalArgumentException().isThrownBy(() -> item.resolve("Repaired", NOW.minusSeconds(1)));
    assertThatIllegalArgumentException().isThrownBy(() -> item.markUnrepairable("No parts", null));
    assertThat(item.snapshot()).isEqualTo(before);
  }

  @Test
  void copiesDoNotShareMutableLifecycleState() {
    var original = newCase(null);
    var copy = original.copy();
    copy.assign(TECHNICIAN);
    assertThat(original.status()).isEqualTo(MaintenanceStatus.OPEN);
    assertThat(original.assignedTo()).isNull();
  }

  @Test
  void domainTransitionsDoNotIncrementDatabaseOwnedVersion() {
    var item = MaintenanceCase.restore(new MaintenanceCaseSnapshot(ID, EQUIPMENT, REPORTER, null,
        LOAN, MaintenanceStatus.OPEN, "Fault", null, NOW, null, 7));
    item.assign(TECHNICIAN);
    item.start();
    item.resolve("Repaired", NOW);
    assertThat(item.version()).isEqualTo(7);
  }
}
