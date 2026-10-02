package sg.edu.nus.serms.maintenance.integration;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import sg.edu.nus.serms.maintenance.domain.*;
import sg.edu.nus.serms.maintenance.repository.*;
import sg.edu.nus.serms.maintenance.repository.jdbc.*;
import sg.edu.nus.serms.maintenance.service.*;
import sg.edu.nus.serms.maintenance.service.jdbc.*;

/** UC04 only. Requires an explicitly supplied, already migrated TEST PostgreSQL database. */
final class Uc04PostgresTestSupport implements AutoCloseable {
  final AnnotationConfigApplicationContext context;
  final JdbcTemplate jdbc;
  final TransactionTemplate transactions;
  final MaintenanceService service;
  final JdbcMaintenanceCaseRepository cases;
  final JdbcMaintenanceHistoryRepository history;
  final JdbcEquipmentMaintenanceAdapter equipment;
  final CaseReads reads;
  final MutableClock clock;
  final CapturedEvents events;
  final TestEventPublisher publisher;
  final Identifiers identifiers;
  final List<UUID> users = new ArrayList<>(), equipmentIds = new ArrayList<>(),
      reservationIds = new ArrayList<>(), loanIds = new ArrayList<>(), auditIds = new ArrayList<>();

  Uc04PostgresTestSupport() {
    context = new AnnotationConfigApplicationContext(Config.class);
    try {
      jdbc = context.getBean(JdbcTemplate.class);
      verifyDatabase(); // No fixture writes until every precondition has passed.
      transactions = new TransactionTemplate(context.getBean(DataSourceTransactionManager.class));
      transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
      transactions.setTimeout(30);
      service = context.getBean(MaintenanceService.class);
      cases = context.getBean(JdbcMaintenanceCaseRepository.class);
      history = context.getBean(JdbcMaintenanceHistoryRepository.class);
      equipment = context.getBean(JdbcEquipmentMaintenanceAdapter.class);
      reads = context.getBean(CaseReads.class);
      clock = context.getBean(MutableClock.class);
      events = context.getBean(CapturedEvents.class);
      publisher = context.getBean(TestEventPublisher.class);
      identifiers = context.getBean(Identifiers.class);
    } catch (RuntimeException | Error failure) {
      context.close();
      throw failure;
    }
  }

  private void verifyDatabase() {
    String database = jdbc.queryForObject("SELECT current_database()", String.class);
    // A named test database is an additional guard against accidental live-database fixtures.
    if (database == null || !database.matches("serms_(uc04_)?test(_[a-z0-9_]+)?")) {
      throw new IllegalStateException("UC04 requires an isolated TEST database named serms_test "
          + "or serms_uc04_test (optionally followed by an underscore and run suffix)");
    }
    try {
      Integer version = jdbc.queryForObject("SELECT max(version) FROM serms.schema_version", Integer.class);
      if (version == null || version < 2) {
        throw new IllegalStateException("UC04 requires repository V001 and V002 already applied; V002 is absent");
      }
    } catch (DataAccessException absentSchema) {
      throw new IllegalStateException("Apply repository V001__sprint1.sql and V002__align_serms_model.sql "
          + "to the TEST database before enabling UC04 PostgreSQL tests", absentSchema);
    }
  }

  UUID user(String accountStatus, String role) {
    UUID id = UUID.randomUUID();
    users.add(id);
    transactions.executeWithoutResult(tx -> {
      jdbc.update("""
          INSERT INTO serms.app_user(user_id, email, display_name, password_hash, account_status)
          VALUES (?, ?, 'UC04 test user', 'TEST_ONLY_NOT_A_LOGIN_HASH', ?)
          """, id, id + "@example.invalid", accountStatus);
      UUID roleId = jdbc.queryForObject("SELECT role_id FROM serms.role WHERE code = ?", UUID.class, role);
      jdbc.update("INSERT INTO serms.user_role(user_id, role_id) VALUES (?, ?)", id, roleId);
    });
    return id;
  }

  UUID equipment() {
    UUID id = UUID.randomUUID();
    equipmentIds.add(id);
    jdbc.update("""
        INSERT INTO serms.equipment(equipment_id, asset_tag, name, category, location)
        VALUES (?, ?, 'UC04 test camera', 'Camera', 'Test lab')
        """, id, "uc04-it-" + id);
    return id;
  }

  UUID activeLoan(UUID equipmentId, UUID borrower) {
    UUID reservation = UUID.randomUUID(), loan = UUID.randomUUID();
    reservationIds.add(reservation);
    loanIds.add(loan);
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS), due = now.plusSeconds(3600);
    transactions.executeWithoutResult(tx -> {
      equipment.lockForMaintenance(equipmentId);
      jdbc.update("""
          INSERT INTO serms.reservation(reservation_id, requester_id, equipment_id, start_at, end_at, status)
          VALUES (?, ?, ?, ?, ?, 'CONFIRMED')
          """, reservation, borrower, equipmentId, Timestamp.from(now.minusSeconds(60)), Timestamp.from(due));
      jdbc.update("""
          INSERT INTO serms.loan(loan_id, reservation_id, checkout_by, checked_out_at, due_at)
          VALUES (?, ?, ?, ?, ?)
          """, loan, reservation, borrower, Timestamp.from(now.minusSeconds(30)), Timestamp.from(due));
      jdbc.update("UPDATE serms.reservation SET status = 'FULFILLED' WHERE reservation_id = ?", reservation);
      jdbc.update("UPDATE serms.equipment SET status = 'ON_LOAN' WHERE equipment_id = ?", equipmentId);
    });
    return loan;
  }

  MaintenanceCase report(UUID equipmentId, UUID reporter) {
    clock.advance();
    return service.reportFault(equipmentId, reporter, "Broken cable");
  }

  MaintenanceCase started(UUID equipmentId, UUID reporter, UUID technician) {
    var item = report(equipmentId, reporter);
    clock.advance();
    service.assignTechnician(item.maintenanceCaseId(), technician, reporter);
    clock.advance();
    return service.startMaintenance(item.maintenanceCaseId(), technician);
  }

  String equipmentStatus(UUID id) {
    return jdbc.queryForObject("SELECT status FROM serms.equipment WHERE equipment_id = ?", String.class, id);
  }

  int equipmentVersion(UUID id) {
    return jdbc.queryForObject("SELECT version FROM serms.equipment WHERE equipment_id = ?", Integer.class, id);
  }

  int actionCount(UUID caseId, String action) {
    return jdbc.queryForObject("""
        SELECT count(*) FROM serms.audit_log
        WHERE entity_type = 'MaintenanceCase' AND entity_id = ? AND action = ?
        """, Integer.class, caseId, action);
  }

  void unrelatedAudit(UUID caseId, UUID actor) {
    UUID audit = UUID.randomUUID();
    auditIds.add(audit);
    jdbc.update("""
        INSERT INTO serms.audit_log(audit_id, actor_id, action, entity_type, entity_id,
          outcome, request_id, change_summary, occurred_at)
        VALUES (?, ?, 'UNRELATED_GLOBAL_ACTION', 'MaintenanceCase', ?, 'SUCCESS', ?, 'Ignore this', ?)
        """, audit, actor, caseId, "uc04-test:" + audit, Timestamp.from(clock.instant()));
  }

  @Override
  public void close() {
    try {
      cleanup();
    } finally {
      context.close();
    }
  }

  private void cleanup() {
    // V002 audit rows cannot be deleted. Retention of our audit rows and referenced users
    // is explicitly approved; never disable triggers or alter the append-only contract.
    for (UUID id : identifiers.created) {
      auditIds.addAll(jdbc.queryForList("SELECT audit_id FROM serms.audit_log WHERE entity_id = ?",
          UUID.class, id));
    }
    transactions.executeWithoutResult(tx -> {
      for (UUID id : identifiers.created) jdbc.update("DELETE FROM serms.maintenance_case WHERE maintenance_case_id = ?", id);
      for (UUID id : loanIds) jdbc.update("DELETE FROM serms.loan WHERE loan_id = ?", id);
      for (UUID id : reservationIds) jdbc.update("DELETE FROM serms.reservation WHERE reservation_id = ?", id);
      for (UUID id : equipmentIds) jdbc.update("DELETE FROM serms.equipment WHERE equipment_id = ?", id);
      for (UUID id : users) {
        jdbc.update("DELETE FROM serms.user_role WHERE user_id = ?", id);
        jdbc.update("""
            DELETE FROM serms.app_user u WHERE u.user_id = ?
            AND NOT EXISTS (SELECT 1 FROM serms.audit_log a WHERE a.actor_id = u.user_id)
            """, id);
      }
    });
  }

  @TestConfiguration
  @EnableTransactionManagement(proxyTargetClass = true)
  @Import({MaintenanceService.class, JdbcMaintenanceCaseRepository.class, JdbcMaintenanceHistoryRepository.class,
      JdbcEquipmentMaintenanceAdapter.class, JdbcTechnicianDirectoryAdapter.class, SpringMaintenanceEventPublisher.class})
  static class Config {
    @Bean
    DataSource dataSource() {
      String url = required("SERMS_UC04_TEST_JDBC_URL");
      if (!url.startsWith("jdbc:postgresql://")) {
        throw new IllegalStateException("SERMS_UC04_TEST_JDBC_URL must explicitly target TEST PostgreSQL");
      }
      var source = new DriverManagerDataSource(url, required("SERMS_UC04_TEST_DB_USER"),
          required("SERMS_UC04_TEST_DB_PASSWORD"));
      source.setDriverClassName("org.postgresql.Driver");
      var properties = new Properties();
      properties.setProperty("connectTimeout", "10");
      properties.setProperty("socketTimeout", "30");
      properties.setProperty("ApplicationName", "serms-uc04-postgres-it");
      source.setConnectionProperties(properties);
      return source;
    }
    @Bean JdbcTemplate jdbcTemplate(DataSource source) { return new JdbcTemplate(source); }
    @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
    @Bean("maintenanceClock") MutableClock clock() { return new MutableClock(); }
    @Bean("maintenanceIdentifiers") Identifiers identifiers() { return new Identifiers(); }
    @Bean CapturedEvents capturedEvents() { return new CapturedEvents(); }
    @Bean @Primary CaseReads caseReads(JdbcMaintenanceCaseRepository cases, JdbcTemplate jdbc) { return new CaseReads(cases, jdbc); }
    @Bean @Primary TestEventPublisher eventPublisher(SpringMaintenanceEventPublisher delegate) { return new TestEventPublisher(delegate); }

    private static String required(String name) {
      String value = System.getenv(name);
      if (value == null || value.isBlank()) {
        throw new IllegalStateException("Explicit UC04 PostgreSQL profile requires environment variable " + name);
      }
      return value;
    }
  }

  static final class MutableClock extends Clock {
    private Instant time = Instant.now().truncatedTo(ChronoUnit.MICROS);
    void advance() { time = time.plusSeconds(1); }
    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) {
      if (!ZoneOffset.UTC.equals(zone)) throw new IllegalArgumentException("UC04 test clock uses UTC");
      return this;
    }
    @Override public Instant instant() { return time; }
  }

  static final class Identifiers implements Supplier<UUID> {
    final ConcurrentLinkedQueue<UUID> created = new ConcurrentLinkedQueue<>();
    @Override public UUID get() {
      UUID id = UUID.randomUUID();
      created.add(id);
      return id;
    }
  }

  static final class CapturedEvents {
    final List<MaintenanceAssigned> assigned = new CopyOnWriteArrayList<>();
    final List<MaintenanceCompleted> completed = new CopyOnWriteArrayList<>();
    @EventListener public void assigned(MaintenanceAssigned event) { assigned.add(event); }
    @EventListener public void completed(MaintenanceCompleted event) { completed.add(event); }
  }

  /** Only the failure test replaces publication behaviour; all normal events use the real bridge. */
  static final class TestEventPublisher implements MaintenanceEventPublisher {
    private final SpringMaintenanceEventPublisher delegate;
    Consumer<MaintenanceCompleted> beforeCompleted = event -> {};
    TestEventPublisher(SpringMaintenanceEventPublisher delegate) { this.delegate = delegate; }
    @Override public void publish(MaintenanceAssigned event) { delegate.publish(event); }
    @Override public void publish(MaintenanceCompleted event) {
      beforeCompleted.accept(event);
      delegate.publish(event);
    }
  }

  /** Delegates every persistence operation to real JDBC; hooks only control snapshot timing. */
  static final class CaseReads implements MaintenanceCaseRepository {
    private final JdbcMaintenanceCaseRepository delegate;
    private final JdbcTemplate jdbc;
    final AtomicReference<MaintenanceCase> replayOnce = new AtomicReference<>();
    volatile CompetingReads competing;
    CaseReads(JdbcMaintenanceCaseRepository delegate, JdbcTemplate jdbc) { this.delegate = delegate; this.jdbc = jdbc; }
    @Override public MaintenanceCase insert(MaintenanceCase item) { return delegate.insert(item); }
    @Override public MaintenanceCase update(MaintenanceCase item) { return delegate.update(item); }
    @Override public Optional<MaintenanceCase> findById(UUID id) {
      var persisted = delegate.findById(id);
      var replay = replayOnce.get();
      if (replay != null && replay.maintenanceCaseId().equals(id) && replayOnce.compareAndSet(replay, null)) {
        return Optional.of(replay.copy());
      }
      var coordination = competing;
      if (coordination != null && coordination.caseId.equals(id)) {
        coordination.versions.add(persisted.orElseThrow().version());
        coordination.backends.add(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
        try {
          coordination.barrier.await(15, TimeUnit.SECONDS);
        } catch (Exception failure) {
          if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
          throw new IllegalStateException("Both competing maintenance snapshots must be loaded before either Equipment lock", failure);
        }
      }
      return persisted;
    }
  }

  static final class CompetingReads {
    final UUID caseId;
    final CyclicBarrier barrier = new CyclicBarrier(2);
    final ConcurrentLinkedQueue<Integer> versions = new ConcurrentLinkedQueue<>(), backends = new ConcurrentLinkedQueue<>();
    CompetingReads(UUID caseId) { this.caseId = caseId; }
  }
}
