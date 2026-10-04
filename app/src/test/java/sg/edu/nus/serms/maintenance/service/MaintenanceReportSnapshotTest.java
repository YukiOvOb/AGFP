package sg.edu.nus.serms.maintenance.service;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import sg.edu.nus.serms.maintenance.domain.MaintenanceStatus;

class MaintenanceReportSnapshotTest {
  private static final Instant FROM = Instant.parse("2026-10-04T00:00:00Z"), TO = FROM.plusSeconds(60);

  private MaintenanceReportSnapshot snapshot(Map<MaintenanceStatus, Long> counts, long reported,
      long completed, Optional<Duration> average) {
    return new MaintenanceReportSnapshot(FROM, TO, counts, reported, completed, average);
  }

  @Test
  void acceptsZeroCountsAndEmptyAverageAndIncludesAllStatuses() {
    var report = snapshot(Map.of(), 0, 0, Optional.empty());
    assertThat(report.casesByStatus()).hasSize(5).containsOnlyKeys(MaintenanceStatus.values());
    assertThat(report.casesByStatus().values()).containsOnly(0L);
    assertThat(report.reportedCaseCount()).isZero();
    assertThat(report.completedCaseCount()).isZero();
    assertThat(report.averageCompletionDuration()).isEmpty();
  }

  @Test
  void defensivelyCopiesMapAndRejectsMutation() {
    var source = new EnumMap<MaintenanceStatus, Long>(MaintenanceStatus.class);
    source.put(MaintenanceStatus.OPEN, 3L);
    var report = snapshot(source, 1, 0, Optional.empty());
    source.clear();
    assertThat(report.casesByStatus().get(MaintenanceStatus.OPEN)).isEqualTo(3);
    assertThatThrownBy(() -> report.casesByStatus().put(MaintenanceStatus.OPEN, 4L))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void preservesAllFiveStatusCountsIndependentlyOfIntervalTotals() {
    var counts = new EnumMap<MaintenanceStatus, Long>(MaintenanceStatus.class);
    for (var status : MaintenanceStatus.values()) counts.put(status, (long) status.ordinal() + 1);
    assertThat(snapshot(counts, 0, 0, Optional.empty()).casesByStatus()).isEqualTo(counts);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2})
  void rejectsNegativeStatusReportedOrCompletedCount(int field) {
    assertThatIllegalArgumentException().isThrownBy(() -> snapshot(
        Map.of(MaintenanceStatus.OPEN, field == 0 ? -1L : 0L), field == 1 ? -1 : 0,
        field == 2 ? -1 : 0, Optional.empty()));
  }

  @ParameterizedTest
  @ValueSource(longs = {0, 1})
  void averageMustBePresentExactlyWhenCompletedCountIsPositive(long completed) {
    assertThatIllegalArgumentException().isThrownBy(() -> snapshot(Map.of(), 0, completed,
        completed == 0 ? Optional.of(Duration.ZERO) : Optional.empty()));
  }

  @Test
  void rejectsNegativeAverageDuration() {
    assertThatIllegalArgumentException().isThrownBy(() -> snapshot(Map.of(), 0, 1,
        Optional.of(Duration.ofNanos(-1))));
  }

  @Test
  void acceptsZeroAndSubSecondCompletionDurations() {
    assertThat(snapshot(Map.of(), 0, 1, Optional.of(Duration.ZERO)).averageCompletionDuration())
        .contains(Duration.ZERO);
    var duration = Duration.ofNanos(123_456_789);
    assertThat(snapshot(Map.of(), 0, 2, Optional.of(duration)).averageCompletionDuration()).contains(duration);
  }

  @ParameterizedTest
  @ValueSource(strings = {"from", "to", "equal", "reversed"})
  void rejectsInvalidRange(String invalid) {
    Instant from = invalid.equals("from") ? null : FROM;
    Instant to = switch (invalid) {
      case "to" -> null;
      case "equal" -> FROM;
      case "reversed" -> FROM.minusSeconds(1);
      default -> TO;
    };
    assertThatThrownBy(() -> new MaintenanceReportSnapshot(from, to, Map.of(), 0, 0, Optional.empty()))
        .isInstanceOf(invalid.equals("from") || invalid.equals("to")
            ? NullPointerException.class : IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"map", "status", "count", "optional"})
  void rejectsNullMapEntriesAndOptional(String field) {
    var counts = new HashMap<MaintenanceStatus, Long>();
    if (field.equals("status")) counts.put(null, 0L);
    if (field.equals("count")) counts.put(MaintenanceStatus.OPEN, null);
    assertThatNullPointerException().isThrownBy(() -> snapshot(field.equals("map") ? null : counts,
        0, 0, field.equals("optional") ? null : Optional.empty()));
  }
}
