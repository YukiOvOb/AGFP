package sg.edu.nus.serms.maintenance.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.jdbc.core.JdbcOperations;
import sg.edu.nus.serms.maintenance.domain.*;
import sg.edu.nus.serms.maintenance.repository.*;
import sg.edu.nus.serms.maintenance.repository.jdbc.*;
import sg.edu.nus.serms.maintenance.service.jdbc.*;

class MaintenanceJdbcWiringTest {
  @Configuration
  @EnableTransactionManagement(proxyTargetClass = true)
  static class TransactionConfig {}

  private final JdbcOperations jdbc = mock(JdbcOperations.class);

  private AnnotationConfigApplicationContext context() {
    var context = new AnnotationConfigApplicationContext();
    context.register(TransactionConfig.class, MaintenanceConfiguration.class, MaintenanceService.class,
        SpringMaintenanceEventPublisher.class, JdbcMaintenanceCaseRepository.class,
        JdbcMaintenanceHistoryRepository.class, JdbcEquipmentMaintenanceAdapter.class,
        JdbcTechnicianDirectoryAdapter.class);
    context.registerBean(JdbcOperations.class, () -> jdbc);
    context.registerBean("transactionManager", MaintenanceTransactionTest.RecordingTransactions.class,
        MaintenanceTransactionTest.RecordingTransactions::new);
    // Unrelated module beans deliberately coexist with the qualified UC04 dependencies.
    context.registerBean("otherClock", Clock.class, Clock::systemDefaultZone);
    context.registerBean("otherIdentifiers", Supplier.class, () -> UUID::randomUUID);
    context.refresh();
    return context;
  }

  @Test
  void wiresProductionAdaptersAndQualifiedDependenciesWithoutQueryingDatabase() {
    try (var context = context()) {
      assertThat(AopUtils.isCglibProxy(context.getBean(MaintenanceService.class))).isTrue();
      assertThat(context.getBean(MaintenanceCaseRepository.class)).isInstanceOf(JdbcMaintenanceCaseRepository.class);
      assertThat(context.getBean(MaintenanceHistoryRepository.class)).isInstanceOf(JdbcMaintenanceHistoryRepository.class);
      assertThat(context.getBean(EquipmentMaintenancePort.class)).isInstanceOf(JdbcEquipmentMaintenanceAdapter.class);
      assertThat(context.getBean(TechnicianDirectoryPort.class)).isInstanceOf(JdbcTechnicianDirectoryAdapter.class);
      assertThat(context.getBean("maintenanceClock", Clock.class).getZone()).isEqualTo(java.time.ZoneOffset.UTC);
      assertThat(context.getBean("maintenanceIdentifiers", Supplier.class).get()).isInstanceOf(UUID.class);
      verifyNoInteractions(jdbc);
    }
  }

  @Test
  void caseAndAuditWritesRequireExistingTransaction() {
    UUID id = new UUID(0, 1);
    var item = new MaintenanceCase(id, id, id, null, "Fault", java.time.Instant.now());
    try (var context = context()) {
      var cases = context.getBean(MaintenanceCaseRepository.class);
      assertThatThrownBy(() -> cases.insert(item)).isInstanceOf(IllegalTransactionStateException.class);
      assertThatThrownBy(() -> cases.update(item)).isInstanceOf(IllegalTransactionStateException.class);
      var history = context.getBean(MaintenanceHistoryRepository.class);
      assertThatThrownBy(() -> history.append(new MaintenanceHistoryEntry(id, id,
          MaintenanceHistoryAction.REPORT_FAULT, null, MaintenanceStatus.OPEN, id,
          item.reportedAt(), "Fault"))).isInstanceOf(IllegalTransactionStateException.class);
      verifyNoInteractions(jdbc);
    }
  }

  @Test
  void equipmentLocksAndWritesRequireExistingTransaction() {
    UUID id = new UUID(0, 1);
    try (var context = context()) {
      var equipment = context.getBean(EquipmentMaintenancePort.class);
      assertThatThrownBy(() -> equipment.lockForMaintenance(id)).isInstanceOf(IllegalTransactionStateException.class);
      assertThatThrownBy(() -> equipment.markUnderMaintenance(id)).isInstanceOf(IllegalTransactionStateException.class);
      assertThatThrownBy(() -> equipment.recomputeAfterResolvedMaintenance(id)).isInstanceOf(IllegalTransactionStateException.class);
      assertThatThrownBy(() -> equipment.markRetired(id)).isInstanceOf(IllegalTransactionStateException.class);
      verifyNoInteractions(jdbc);
    }
  }
}
