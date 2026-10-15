package sg.edu.nus.serms.report.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sg.edu.nus.serms.maintenance.domain.MaintenanceStatus;
import sg.edu.nus.serms.maintenance.service.MaintenanceReportService;
import sg.edu.nus.serms.maintenance.service.MaintenanceReportSnapshot;
import sg.edu.nus.serms.report.domain.OperationsReport.CategoryUtilisation;
import sg.edu.nus.serms.report.repository.ReportRepository;
import sg.edu.nus.serms.report.repository.ReportRepository.LoanedTime;

/** Period handling and ratios with the database replaced; the SQL is in ReportPostgresTest. */
class ReportServiceTest {
  static final Instant NOW = Instant.parse("2026-10-14T00:00:00Z");
  final ReportRepository repository = mock(ReportRepository.class);
  final MaintenanceReportService maintenance = mock(MaintenanceReportService.class);
  final ReportService service =
      new ReportService(repository, maintenance, Clock.fixed(NOW, ZoneOffset.UTC));

  LoanedTime item(String category, String name, Duration loaned) {
    return new LoanedTime(UUID.randomUUID(), name.toUpperCase(), name, category, "AVAILABLE", 1, loaned);
  }

  @BeforeEach
  void setUp() {
    when(maintenance.getMaintenanceReport(any(), any()))
        .thenAnswer(
            a ->
                new MaintenanceReportSnapshot(
                    a.getArgument(0),
                    a.getArgument(1),
                    Map.of(MaintenanceStatus.OPEN, 2L),
                    3,
                    1,
                    Optional.of(Duration.ofHours(5))));
  }

  @Test
  void rejectsAnEmptyReversedOrOverlongPeriod() {
    assertThatThrownBy(() -> service.report(NOW, NOW)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.report(NOW, NOW.minusSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.report(NOW.minus(Duration.ofDays(367)), NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.report(null, NOW)).isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(repository);
    assertThat(service.report(NOW.minus(Duration.ofDays(366)), NOW)).isNotNull();
  }

  @Test
  void measuresUtilisationOnlyUpToNow() {
    Instant from = NOW.minus(Duration.ofDays(10));
    when(repository.loanedTime(from, NOW))
        .thenReturn(List.of(item("Audio", "Mic", Duration.ofDays(5)), item("Audio", "Speaker", Duration.ZERO)));
    var report = service.report(from, NOW.plus(Duration.ofDays(20)));
    assertThat(report.measuredUntil()).isEqualTo(NOW);
    assertThat(report.generatedAt()).isEqualTo(NOW);
    assertThat(report.equipmentUtilisation()).extracting(e -> e.utilisation()).containsExactly(0.5, 0.0);
    assertThat(report.utilisationByCategory())
        .singleElement()
        .satisfies(
            c -> {
              assertThat(c.equipment()).isEqualTo(2);
              assertThat(c.loanedTime()).isEqualTo(Duration.ofDays(5));
              assertThat(c.utilisation()).isEqualTo(0.25);
            });
    // Overdue frequency stops at now as well; overdue loans are measured at now.
    verify(repository).overdueByCategory(from, NOW);
    verify(repository).overdueAt(NOW);
    // Reservations and maintenance use the whole period, including the part still ahead.
    verify(repository).reservationsByCategory(from, NOW.plus(Duration.ofDays(20)));
  }

  @Test
  void aPeriodEntirelyAheadHasNothingMeasured() {
    Instant from = NOW.plus(Duration.ofDays(1));
    when(repository.loanedTime(NOW, NOW)).thenReturn(List.of(item("Audio", "Mic", Duration.ZERO)));
    var report = service.report(from, from.plus(Duration.ofDays(7)));
    assertThat(report.measuredUntil()).isEqualTo(NOW);
    assertThat(report.equipmentUtilisation()).extracting(e -> e.utilisation()).containsExactly(0.0);
    verify(repository).overdueByCategory(NOW, NOW);
  }

  @Test
  void categoriesAreSortedBusiestFirst() {
    Instant from = NOW.minus(Duration.ofDays(1));
    when(repository.loanedTime(from, NOW))
        .thenReturn(
            List.of(
                item("Audio", "Mic", Duration.ofHours(6)),
                item("Camera", "Body", Duration.ofHours(12)),
                item("Camera", "Lens", Duration.ofHours(12)),
                item("Audio", "Speaker", Duration.ZERO)));
    assertThat(service.report(from, NOW).utilisationByCategory())
        .extracting(CategoryUtilisation::category, CategoryUtilisation::utilisation)
        .containsExactly(tuple("Camera", 0.5), tuple("Audio", 0.125));
  }

  @Test
  void passesMaintenanceFiguresThrough() {
    var report = service.report(NOW.minus(Duration.ofDays(1)), NOW);
    assertThat(report.maintenance().casesByStatus()).containsEntry("OPEN", 2L);
    assertThat(report.maintenance().reportedInPeriod()).isEqualTo(3);
    assertThat(report.maintenance().completedInPeriod()).isEqualTo(1);
    assertThat(report.maintenance().averageCompletion()).isEqualTo(Duration.ofHours(5));
    verify(repository).maintenanceByEquipment(any(), any(), eq(10));
  }

  @Test
  void ratioIsBoundedAndSafeForAnEmptyWindow() {
    assertThat(ReportService.ratio(Duration.ofHours(2), Duration.ofHours(1))).isEqualTo(1.0);
    assertThat(ReportService.ratio(Duration.ofHours(1), Duration.ZERO)).isEqualTo(0.0);
    assertThat(ReportService.ratio(Duration.ofMinutes(15), Duration.ofHours(1))).isEqualTo(0.25);
  }
}
