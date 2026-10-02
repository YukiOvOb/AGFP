package sg.edu.nus.serms.maintenance.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import sg.edu.nus.serms.maintenance.domain.*;
import sg.edu.nus.serms.maintenance.repository.*;

/** Exercises the real Spring proxy; no claim of database rollback is made by these mocks. */
class MaintenanceTransactionTest {
  private static final UUID CASE = new UUID(0, 1), EQUIPMENT = new UUID(0, 2),
      REPORTER = new UUID(0, 3), TECHNICIAN = new UUID(0, 4);
  private static final Instant NOW = Instant.parse("2026-10-02T02:00:00Z");
  private final MaintenanceCaseRepository cases = mock(MaintenanceCaseRepository.class);
  private final MaintenanceHistoryRepository history = mock(MaintenanceHistoryRepository.class);
  private final EquipmentMaintenancePort equipment = mock(EquipmentMaintenancePort.class);
  private final TechnicianDirectoryPort technicians = mock(TechnicianDirectoryPort.class);
  private final MaintenanceEventPublisher events = mock(MaintenanceEventPublisher.class);
  private final RecordingTransactions transactions = new RecordingTransactions();

  @Configuration
  @EnableTransactionManagement(proxyTargetClass = true)
  static class TransactionConfig {}

  private AnnotationConfigApplicationContext context() {
    var context = new AnnotationConfigApplicationContext();
    context.register(TransactionConfig.class);
    context.registerBean("transactionManager", RecordingTransactions.class, () -> transactions);
    context.registerBean(MaintenanceCaseRepository.class, () -> cases);
    context.registerBean(MaintenanceHistoryRepository.class, () -> history);
    context.registerBean(EquipmentMaintenancePort.class, () -> equipment);
    context.registerBean(TechnicianDirectoryPort.class, () -> technicians);
    context.registerBean(MaintenanceEventPublisher.class, () -> events);
    context.registerBean("maintenanceIdentifiers", java.util.function.Supplier.class, () -> () -> CASE);
    context.registerBean("maintenanceClock", Clock.class, () -> Clock.fixed(NOW, ZoneOffset.UTC));
    context.register(MaintenanceService.class);
    context.refresh();
    return context;
  }

  @BeforeEach
  void persistedOperationsRequireActiveTransaction() {
    doAnswer(call -> { assertTransaction(); return null; }).when(equipment).lockForMaintenance(EQUIPMENT);
    when(cases.insert(any())).thenAnswer(call -> { assertTransaction(); return call.getArgument(0); });
    when(cases.update(any())).thenAnswer(call -> { assertTransaction(); return call.getArgument(0); });
    doAnswer(call -> { assertTransaction(); return null; }).when(history).append(any());
    doAnswer(call -> { assertTransaction(); return null; }).when(events).publish(any(MaintenanceAssigned.class));
    doAnswer(call -> { assertTransaction(); return null; }).when(events).publish(any(MaintenanceCompleted.class));
  }

  private void assertTransaction() {
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
    assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
  }

  private MaintenanceCase startedCase() {
    var item = new MaintenanceCase(CASE, EQUIPMENT, REPORTER, null, "Fault", NOW);
    item.assign(TECHNICIAN);
    item.start();
    return item;
  }

  @Test
  void reportRunsAllAdaptersInOneSpringTransaction() {
    try (var context = context()) {
      var service = context.getBean(MaintenanceService.class);
      assertThat(AopUtils.isCglibProxy(service)).isTrue();
      service.reportFault(EQUIPMENT, REPORTER, "Fault");
      assertThat(transactions.begins).isEqualTo(1);
      assertThat(transactions.commits).isEqualTo(1);
      assertThat(transactions.rollbacks).isZero();
      var order = inOrder(equipment, cases, history);
      order.verify(equipment).ensureEquipmentExists(EQUIPMENT);
      order.verify(equipment).lockForMaintenance(EQUIPMENT);
      order.verify(equipment).markUnderMaintenance(EQUIPMENT);
      order.verify(cases).insert(any());
      order.verify(history).append(any());
    }
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"insert", "audit"})
  void reportFailureRollsBackSpringBoundary(String failure) {
    if (failure.equals("insert")) doThrow(new IllegalStateException("Insert failed")).when(cases).insert(any());
    else doThrow(new IllegalStateException("Audit failed")).when(history).append(any());
    try (var context = context()) {
      assertThatThrownBy(() -> context.getBean(MaintenanceService.class).reportFault(EQUIPMENT, REPORTER, "Fault"))
          .isInstanceOf(IllegalStateException.class);
      assertThat(transactions.commits).isZero();
      assertThat(transactions.rollbacks).isEqualTo(1);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"audit", "event"})
  void completionFailureRollsBackSpringBoundary(String failure) {
    when(cases.findById(CASE)).thenReturn(Optional.of(startedCase()));
    if (failure.equals("audit")) doThrow(new IllegalStateException("Audit failed")).when(history).append(any());
    else doThrow(new IllegalStateException("Event failed")).when(events).publish(any(MaintenanceCompleted.class));
    try (var context = context()) {
      assertThatThrownBy(() -> context.getBean(MaintenanceService.class).resolve(CASE, "Repaired", TECHNICIAN))
          .isInstanceOf(IllegalStateException.class);
      assertThat(transactions.commits).isZero();
      assertThat(transactions.rollbacks).isEqualTo(1);
      var order = inOrder(equipment, cases, history);
      order.verify(equipment).lockForMaintenance(EQUIPMENT);
      order.verify(cases).update(argThat(item -> item.status() == MaintenanceStatus.RESOLVED));
      order.verify(equipment).recomputeAfterResolvedMaintenance(EQUIPMENT);
      order.verify(history).append(any());
      if (failure.equals("audit")) verify(events, never()).publish(any(MaintenanceCompleted.class));
    }
  }

  @Test
  void optimisticConflictRollsBackAndStopsAuditAndEvent() {
    var item = new MaintenanceCase(CASE, EQUIPMENT, REPORTER, null, "Fault", NOW);
    when(cases.findById(CASE)).thenReturn(Optional.of(item));
    doThrow(new MaintenanceVersionConflictException(CASE, 0)).when(cases).update(any());
    try (var context = context()) {
      assertThatThrownBy(() -> context.getBean(MaintenanceService.class).assignTechnician(CASE, TECHNICIAN, REPORTER))
          .isInstanceOf(MaintenanceVersionConflictException.class);
      assertThat(transactions.rollbacks).isEqualTo(1);
      verifyNoInteractions(history, events);
    }
  }

  @Test
  void readsUseReadOnlySpringTransaction() {
    when(cases.findById(CASE)).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
      return Optional.of(startedCase());
    });
    when(history.findByCaseId(CASE)).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
      return java.util.List.of();
    });
    when(history.findByEquipmentId(EQUIPMENT)).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
      return java.util.List.of();
    });
    try (var context = context()) {
      var service = context.getBean(MaintenanceService.class);
      service.getCase(CASE);
      service.getCaseHistory(CASE);
      service.getEquipmentMaintenanceHistory(EQUIPMENT);
      assertThat(transactions.begins).isEqualTo(3);
      assertThat(transactions.commits).isEqualTo(3);
      verifyNoInteractions(equipment, events);
    }
  }

  static class RecordingTransactions extends AbstractPlatformTransactionManager {
    int begins, commits, rollbacks;
    @Override protected Object doGetTransaction() { return new Object(); }
    @Override protected void doBegin(Object tx, TransactionDefinition definition) { begins++; }
    @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
    @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
  }
}
