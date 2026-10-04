package sg.edu.nus.serms.maintenance.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import sg.edu.nus.serms.maintenance.domain.MaintenanceStatus;
import sg.edu.nus.serms.maintenance.repository.MaintenanceReportRepository;

class MaintenanceReportServiceTest {
  private static final Instant FROM = Instant.parse("2026-10-04T00:00:00.123456Z"), TO = FROM.plusSeconds(60);
  private final MaintenanceReportRepository reports = mock(MaintenanceReportRepository.class);
  private final MaintenanceReportService service = new MaintenanceReportService(reports);

  @Test
  void delegatesAllFourPrimitivesWithExactIntervalAndReturnsImmutableSnapshot() {
    var counts = new EnumMap<MaintenanceStatus, Long>(MaintenanceStatus.class);
    for (var status : MaintenanceStatus.values()) counts.put(status, (long) status.ordinal() + 1);
    when(reports.countCasesByStatus()).thenReturn(counts);
    when(reports.countReportedBetween(FROM, TO)).thenReturn(7L);
    when(reports.countCompletedBetween(FROM, TO)).thenReturn(2L);
    when(reports.averageCompletionDurationBetween(FROM, TO)).thenReturn(Optional.of(Duration.ofNanos(123_456_789)));
    var report = service.getMaintenanceReport(FROM, TO);
    assertThat(report.fromInclusive()).isEqualTo(FROM);
    assertThat(report.toExclusive()).isEqualTo(TO);
    assertThat(report.casesByStatus()).isEqualTo(counts).containsOnlyKeys(MaintenanceStatus.values());
    assertThat(report.reportedCaseCount()).isEqualTo(7);
    assertThat(report.completedCaseCount()).isEqualTo(2);
    assertThat(report.averageCompletionDuration()).contains(Duration.ofNanos(123_456_789));
    counts.clear();
    assertThat(report.casesByStatus()).hasSize(5).containsEntry(MaintenanceStatus.UNREPAIRABLE, 5L);
    assertThatThrownBy(() -> report.casesByStatus().clear()).isInstanceOf(UnsupportedOperationException.class);
    var order = inOrder(reports);
    order.verify(reports).countCasesByStatus();
    order.verify(reports).countReportedBetween(FROM, TO);
    order.verify(reports).countCompletedBetween(FROM, TO);
    order.verify(reports).averageCompletionDurationBetween(FROM, TO);
    verifyNoMoreInteractions(reports);
  }

  @Test
  void emptyCompletionPeriodHasNoInventedAverageOrCalendarDefaults() {
    when(reports.countCasesByStatus()).thenReturn(Map.of(MaintenanceStatus.OPEN, 9L));
    when(reports.averageCompletionDurationBetween(FROM, TO)).thenReturn(Optional.empty());
    var report = service.getMaintenanceReport(FROM, TO);
    assertThat(report.completedCaseCount()).isZero();
    assertThat(report.averageCompletionDuration()).isEmpty();
    assertThat(report.casesByStatus()).containsEntry(MaintenanceStatus.OPEN, 9L);
  }

  @ParameterizedTest
  @ValueSource(strings = {"from", "to", "equal", "reversed"})
  void validatesRangeBeforeAnyRepositoryOperation(String invalid) {
    Instant from = invalid.equals("from") ? null : FROM;
    Instant to = switch (invalid) {
      case "to" -> null;
      case "equal" -> FROM;
      case "reversed" -> FROM.minusSeconds(1);
      default -> TO;
    };
    assertThatThrownBy(() -> service.getMaintenanceReport(from, to))
        .isInstanceOf(invalid.equals("from") || invalid.equals("to")
            ? NullPointerException.class : IllegalArgumentException.class);
    verifyNoInteractions(reports);
  }

  @TestConfiguration
  @EnableTransactionManagement(proxyTargetClass = true)
  static class TransactionConfig {}

  @Test
  void realSpringProxyRunsAllPrimitivesInOneReadOnlyRepeatableReadTransaction() {
    var transactions = new RecordingTransactions();
    when(reports.countCasesByStatus()).thenAnswer(call -> { assertReadTransaction(); return Map.of(); });
    when(reports.countReportedBetween(FROM, TO)).thenAnswer(call -> { assertReadTransaction(); return 0L; });
    when(reports.countCompletedBetween(FROM, TO)).thenAnswer(call -> { assertReadTransaction(); return 0L; });
    when(reports.averageCompletionDurationBetween(FROM, TO)).thenAnswer(call -> {
      assertReadTransaction(); return Optional.empty();
    });
    try (var context = new AnnotationConfigApplicationContext()) {
      context.register(TransactionConfig.class, MaintenanceReportService.class);
      context.registerBean(MaintenanceReportRepository.class, () -> reports);
      context.registerBean("transactionManager", RecordingTransactions.class, () -> transactions);
      context.refresh();
      var proxied = context.getBean(MaintenanceReportService.class);
      assertThat(AopUtils.isCglibProxy(proxied)).isTrue();
      proxied.getMaintenanceReport(FROM, TO);
      assertThat(transactions.begins).isEqualTo(1);
      assertThat(transactions.commits).isEqualTo(1);
      assertThat(transactions.readOnly).isTrue();
      assertThat(transactions.isolation).isEqualTo(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
  }

  private void assertReadTransaction() {
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
    assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
    assertThat(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())
        .isEqualTo(TransactionDefinition.ISOLATION_REPEATABLE_READ);
  }

  static class RecordingTransactions extends AbstractPlatformTransactionManager {
    int begins, commits, isolation;
    boolean readOnly;
    @Override protected Object doGetTransaction() { return new Object(); }
    @Override protected void doBegin(Object tx, TransactionDefinition definition) {
      begins++;
      readOnly = definition.isReadOnly();
      isolation = definition.getIsolationLevel();
    }
    @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
    @Override protected void doRollback(DefaultTransactionStatus status) {}
  }
}
