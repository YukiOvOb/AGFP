package sg.edu.nus.serms.approval.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import sg.edu.nus.serms.approval.domain.ApprovalCommand;
import sg.edu.nus.serms.approval.domain.ApprovalDecisionType;
import sg.edu.nus.serms.approval.service.*;
import sg.edu.nus.serms.approval.service.event.ReservationDecided;
import sg.edu.nus.serms.shared.security.SermsUserPrincipal;
import sg.edu.nus.serms.testsupport.SermsFixtures;

/** Real defaults, Flyway, security, reservation/decision/audit rows and notification adapter. */
@SpringBootTest(properties = {
    "serms.notifications.worker-enabled=false", "serms.security.secure-cookies=false",
    "server.servlet.session.cookie.secure=false"})
@AutoConfigureMockMvc
@Import(ApprovalPostgresIT.EventConfig.class)
class ApprovalPostgresIT {
  static PostgreSQLContainer container;

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    String url = System.getenv("SERMS_UC02_TEST_JDBC_URL");
    String username = System.getenv("SERMS_UC02_TEST_DB_USER");
    String password = System.getenv("SERMS_UC02_TEST_DB_PASSWORD");
    if (url == null || url.isBlank()) {
      container = new PostgreSQLContainer("postgres:18-alpine")
          .withDatabaseName("serms_test_uc02");
      container.start();
      url = container.getJdbcUrl(); username = container.getUsername(); password = container.getPassword();
    } else if (!url.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]+/serms_test_uc02(_[a-z0-9]+)?(\\?.*)?")) {
      throw new IllegalStateException("UC02 requires an isolated loopback serms_test_uc02 database");
    }
    String jdbcUrl = url, jdbcUser = username, jdbcPassword = password;
    properties.add("spring.datasource.url", () -> jdbcUrl);
    properties.add("spring.datasource.username", () -> jdbcUser);
    properties.add("spring.datasource.password", () -> jdbcPassword);
  }

  @TestConfiguration
  static class EventConfig {
    @Bean RecordedEvents recordedEvents() { return new RecordedEvents(); }
  }
  static class RecordedEvents {
    final List<ReservationDecided> events = new CopyOnWriteArrayList<>();
    @EventListener void record(ReservationDecided event) { events.add(event); }
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired MockMvc mvc;
  @Autowired ApprovalService approvals;
  @Autowired ApprovalDecisionPort decisions;
  @Autowired PlatformTransactionManager transactions;
  @Autowired ApplicationEventPublisher publisher;
  @Autowired RecordedEvents events;
  @Autowired PasswordEncoder passwords;
  @MockitoBean(name = "clock") Clock clock; // Time only; no business or persistence ports are mocked.

  SermsFixtures fixtures;
  UUID borrower, approver, equipment, reservation;
  Instant now, start, end;
  SermsUserPrincipal principal;

  @BeforeEach
  void setup() {
    now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    start = now.plusSeconds(300); end = start.plusSeconds(3600);
    when(clock.instant()).thenReturn(now);
    fixtures = new SermsFixtures(jdbc);
    borrower = fixtures.user("BORROWER"); approver = fixtures.user("APPROVER");
    equipment = fixtures.equipment("UC02 integration camera", "Camera", "Desk", true);
    reservation = fixtures.reservation(borrower, equipment, start, end, "PENDING_APPROVAL");
    principal = identity(approver, "APPROVER");
    authenticate(principal); events.events.clear();
  }

  @AfterEach void clearIdentity() { SecurityContextHolder.clearContext(); }
  @AfterAll static void stopContainer() { if (container != null) container.stop(); }

  static SermsUserPrincipal identity(UUID id, String... roles) {
    return new SermsUserPrincipal(id, id + "@example.invalid", "UC02 test", Set.of(roles));
  }
  static void authenticate(SermsUserPrincipal user) {
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
  }
  ApprovalOutcome decide(ApprovalDecisionType type) {
    return approvals.decide(approver, new ApprovalCommand(reservation, type,
        type == ApprovalDecisionType.REJECTED ? "Not eligible for this time slot" : null, 0));
  }
  long count(String table, String key, Object id) {
    return jdbc.queryForObject("SELECT count(*) FROM serms." + table + " WHERE " + key + "=?", Long.class, id);
  }
  long auditCount() { return count("audit_log", "entity_id", reservation); }
  long requestCount() { return count("notification_request", "recipient", borrower.toString()); }
  void assertUnchanged() {
    assertThat(fixtures.reservationStatus(reservation)).isEqualTo("PENDING_APPROVAL");
    assertThat(jdbc.queryForObject("SELECT version FROM serms.reservation WHERE reservation_id=?", Long.class, reservation)).isZero();
    assertThat(count("approval_decision", "reservation_id", reservation)).isZero();
    assertThat(auditCount()).isZero(); assertThat(requestCount()).isZero();
  }

  @Test
  void approvedDecisionHasStoredTimestampAuditAndOneNotificationButDoesNotIssueEquipment() throws Exception {
    var result = decide(ApprovalDecisionType.APPROVED);
    assertThat(result.reservation().status()).isEqualTo("CONFIRMED");
    assertThat(result.reservation().version()).isEqualTo(1);
    var stored = decisions.findByReservationId(reservation).orElseThrow();
    assertThat(stored).isEqualTo(result.decision());
    assertThat(events.events).hasSize(1);
    assertThat(events.events.get(0).decidedAt()).isEqualTo(stored.decidedAt());
    assertThat(jdbc.queryForObject("SELECT occurred_at FROM serms.audit_log WHERE entity_id=?", java.sql.Timestamp.class, reservation).toInstant()).isEqualTo(stored.decidedAt());
    assertThat(auditCount()).isEqualTo(1); assertThat(requestCount()).isEqualTo(1);
    assertThat(count("loan", "reservation_id", reservation)).isZero();
    assertThat(jdbc.queryForObject("SELECT status FROM serms.equipment WHERE equipment_id=?", String.class, equipment)).isEqualTo("AVAILABLE");
    mvc.perform(get("/api/v1/approvals/{id}", reservation).with(user(principal)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"))
        .andExpect(jsonPath("$.decision.approvalId").value(stored.approvalDecisionId().toString()))
        .andExpect(jsonPath("$.decision.decidedAt").value(stored.decidedAt().toString()));
  }

  @Test
  void rejectionReleasesTheBlockingAllocation() {
    assertThatThrownBy(() -> fixtures.reservation(borrower, equipment, start, end, "PENDING_APPROVAL"))
        .isInstanceOf(DataIntegrityViolationException.class);
    var result = decide(ApprovalDecisionType.REJECTED);
    assertThat(result.reservation().status()).isEqualTo("REJECTED");
    assertThat(result.reservation().version()).isEqualTo(1);
    assertThat(fixtures.reservationStatus(fixtures.reservation(borrower, equipment, start, end, "PENDING_APPROVAL")))
        .isEqualTo("PENDING_APPROVAL");
    assertThat(auditCount()).isEqualTo(1); assertThat(requestCount()).isEqualTo(1);
  }

  @ParameterizedTest @EnumSource(ApprovalDecisionType.class)
  void selfDecisionsAreForbiddenWithoutWrites(ApprovalDecisionType decision) throws Exception {
    jdbc.update("INSERT INTO serms.user_role(user_id,role_id) SELECT ?,role_id FROM serms.role WHERE code='APPROVER'", borrower);
    mvc.perform(post("/api/v1/approvals/{id}/decision", reservation)
        .with(user(identity(borrower, "BORROWER", "APPROVER"))).with(csrf())
        .contentType("application/json").content(body(decision, 0)))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.type").value("/problems/self-approval"));
    assertUnchanged();
  }

  @ParameterizedTest @ValueSource(strings = {"BORROWER", "ADMIN", "MAINTAINER"})
  void otherRolesCannotDecide(String role) throws Exception {
    var actor = fixtures.user(role);
    mvc.perform(post("/api/v1/approvals/{id}/decision", reservation)
        .with(user(identity(actor, role))).with(csrf()).contentType("application/json")
        .content(body(ApprovalDecisionType.APPROVED, 0))).andExpect(status().isForbidden());
    assertUnchanged();
  }

  @Test void anonymousAndMissingCsrfRequestsCannotWrite() throws Exception {
    mvc.perform(post("/api/v1/approvals/{id}/decision", reservation).with(anonymous()).with(csrf())
        .contentType("application/json").content(body(ApprovalDecisionType.APPROVED, 0)))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/approvals/{id}/decision", reservation).with(user(principal))
        .contentType("application/json").content(body(ApprovalDecisionType.APPROVED, 0)))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.type").value("/problems/csrf"));
    assertUnchanged();
  }

  @ParameterizedTest @ValueSource(longs = {0, 1})
  void atOrAfterStartApprovalConflictsButRejectionStillWorks(long elapsed) throws Exception {
    when(clock.instant()).thenReturn(start.plusSeconds(elapsed));
    mvc.perform(post("/api/v1/approvals/{id}/decision", reservation).with(user(principal)).with(csrf())
        .contentType("application/json").content(body(ApprovalDecisionType.APPROVED, 0)))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.type").value("/problems/reservation-expired"))
        .andExpect(jsonPath("$.currentStatus").value("PENDING_APPROVAL"))
        .andExpect(jsonPath("$.currentVersion").value(0));
    assertUnchanged();
    // MockMvc clears the thread's SecurityContext after the HTTP request.
    authenticate(principal);
    decide(ApprovalDecisionType.REJECTED);
    assertThat(fixtures.reservationStatus(reservation)).isEqualTo("REJECTED");
  }

  @Test void maintenanceAfterBookingBlocksApproval() throws Exception {
    fixtures.openMaintenanceCase(equipment, borrower);
    mvc.perform(post("/api/v1/approvals/{id}/decision", reservation).with(user(principal)).with(csrf())
        .contentType("application/json").content(body(ApprovalDecisionType.APPROVED, 0)))
        .andExpect(status().isConflict());
    assertUnchanged();
  }

  @ParameterizedTest
  @ValueSource(strings = {"{\"decision\":\"REJECTED\",\"expectedVersion\":0}",
      "{\"decision\":\"REJECTED\",\"comment\":\"  \",\"expectedVersion\":0}",
      "{\"decision\":\"APPROVED\",\"expectedVersion\":-1}",
      "{\"decision\":\"APPROVED\",\"expectedVersion\":0.5}",
      "{\"decision\":\"APPROVED\",\"expectedVersion\":\"0\"}"})
  void invalidCommandsReturn400BeforeAnyBusinessWrite(String body) throws Exception {
    mvc.perform(post("/api/v1/approvals/{id}/decision", reservation).with(user(principal)).with(csrf())
        .contentType("application/json").content(body)).andExpect(status().isBadRequest());
    assertUnchanged();
  }

  @Test void staleVersionReportsLockedCurrentVersion() throws Exception {
    jdbc.update("UPDATE serms.reservation SET purpose='Updated purpose' WHERE reservation_id=?", reservation);
    mvc.perform(post("/api/v1/approvals/{id}/decision", reservation).with(user(principal)).with(csrf())
        .contentType("application/json").content(body(ApprovalDecisionType.APPROVED, 0)))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.type").value("/problems/version-conflict"))
        .andExpect(jsonPath("$.currentStatus").value("PENDING_APPROVAL"))
        .andExpect(jsonPath("$.currentVersion").value(1));
    assertThat(count("approval_decision", "reservation_id", reservation)).isZero();
    assertThat(auditCount()).isZero(); assertThat(requestCount()).isZero();
  }

  @ParameterizedTest @EnumSource(ApprovalDecisionType.class)
  void conflictingRepeatedDecisionsKeepTheOriginal(ApprovalDecisionType original) throws Exception {
    var first = decide(original).decision();
    var next = original == ApprovalDecisionType.APPROVED ? ApprovalDecisionType.REJECTED : ApprovalDecisionType.APPROVED;
    mvc.perform(post("/api/v1/approvals/{id}/decision", reservation).with(user(principal)).with(csrf())
        .contentType("application/json").content(body(next, 1)))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.type").value("/problems/state-conflict"));
    assertThat(decisions.findByReservationId(reservation)).contains(first);
    assertThat(auditCount()).isEqualTo(1); assertThat(requestCount()).isEqualTo(1);
  }

  @Test void realUniqueConstraintTranslatesOnlyTheFinalDecisionDuplicate() {
    decide(ApprovalDecisionType.APPROVED);
    var tx = new TransactionTemplate(transactions);
    assertThatThrownBy(() -> tx.execute(status -> decisions.record(reservation, approver, ApprovalDecisionType.REJECTED, "Duplicate")))
        .isInstanceOfSatisfying(ApprovalDecisionAlreadyRecordedException.class,
            error -> assertThat(error.reservationId()).isEqualTo(reservation));
    assertThat(count("approval_decision", "reservation_id", reservation)).isEqualTo(1);
    assertThat(auditCount()).isEqualTo(1); assertThat(requestCount()).isEqualTo(1);
    assertThatThrownBy(() -> tx.execute(status -> decisions.record(UUID.randomUUID(), approver, ApprovalDecisionType.APPROVED, null)))
        .isInstanceOf(DataIntegrityViolationException.class).isNotInstanceOf(ApprovalDecisionAlreadyRecordedException.class);
  }

  @Test void duplicateApiResponseReadsAuthoritativeMetadataOnlyAfterRollback() throws Exception {
    // An inconsistent legacy row exercises the DB safety net, rather than the normal state guard.
    UUID original = UUID.randomUUID();
    jdbc.update("INSERT INTO serms.approval_decision(approval_id,reservation_id,approver_id,decision,comment) VALUES (?,?,?,?,?)",
        original, reservation, approver, "REJECTED", "Legacy final decision");
    mvc.perform(post("/api/v1/approvals/{id}/decision", reservation).with(user(principal)).with(csrf())
        .contentType("application/json").content(body(ApprovalDecisionType.APPROVED, 0)))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.type").value("/problems/state-conflict"))
        .andExpect(jsonPath("$.currentStatus").value("PENDING_APPROVAL"))
        .andExpect(jsonPath("$.currentVersion").value(0));
    assertThat(fixtures.reservationStatus(reservation)).isEqualTo("PENDING_APPROVAL");
    assertThat(jdbc.queryForObject("SELECT version FROM serms.reservation WHERE reservation_id=?", Long.class, reservation)).isZero();
    assertThat(decisions.findByReservationId(reservation).orElseThrow().approvalDecisionId()).isEqualTo(original);
    assertThat(auditCount()).isZero(); assertThat(requestCount()).isZero();
    assertThat(events.events).isEmpty();
  }

  @Test void decisionRowsCannotBeChangedOrDeleted() {
    var original = decide(ApprovalDecisionType.REJECTED).decision();
    assertThatThrownBy(() -> jdbc.update("UPDATE serms.approval_decision SET comment='Changed' WHERE reservation_id=?", reservation))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> jdbc.update("DELETE FROM serms.approval_decision WHERE reservation_id=?", reservation))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(decisions.findByReservationId(reservation)).contains(original);
  }

  enum FailedWrite { DECISION, AUDIT, NOTIFICATION }
  @ParameterizedTest @EnumSource(FailedWrite.class)
  void eachFailedWriteRollsBackReservationDecisionAuditAndRequest(FailedWrite failedWrite) {
    // Install a fixture-specific failure only in this disposable test database, never production.
    try (var fault = new DatabaseFault(failedWrite)) {
      assertThatThrownBy(() -> decide(ApprovalDecisionType.APPROVED)).isInstanceOf(DataAccessException.class);
      assertUnchanged();
    }
  }

  final class DatabaseFault implements AutoCloseable {
    final String name = "uc02_fault_" + UUID.randomUUID().toString().replace("-", "");
    final String table;
    DatabaseFault(FailedWrite point) {
      table = switch (point) { case DECISION -> "approval_decision"; case AUDIT -> "audit_log"; case NOTIFICATION -> "notification_request"; };
      String guard = switch (point) {
        case DECISION -> "NEW.reservation_id='" + reservation + "'::uuid";
        case AUDIT -> "NEW.entity_id='" + reservation + "'::uuid AND NEW.action='RESERVATION_DECIDE'";
        case NOTIFICATION -> "NEW.recipient='" + borrower + "'";
      };
      jdbc.execute("CREATE FUNCTION serms." + name + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF "
          + guard + " THEN RAISE EXCEPTION 'UC02 test failure' USING ERRCODE='23514'; END IF; RETURN NEW; END $$");
      jdbc.execute("CREATE TRIGGER " + name + " BEFORE INSERT ON serms." + table
          + " FOR EACH ROW EXECUTE FUNCTION serms." + name + "()");
    }
    @Override public void close() {
      jdbc.execute("DROP TRIGGER " + name + " ON serms." + table);
      jdbc.execute("DROP FUNCTION serms." + name + "()");
    }
  }

  @Test void concurrentApproveAndRejectHaveExactlyOneCommittedWinner() throws Exception {
    UUID second = fixtures.user("APPROVER");
    var pool = Executors.newFixedThreadPool(2); var barrier = new CyclicBarrier(2);
    try {
      List<Callable<Boolean>> attempts = List.of(
          () -> concurrentAttempt(approver, ApprovalDecisionType.APPROVED, barrier),
          () -> concurrentAttempt(second, ApprovalDecisionType.REJECTED, barrier));
      var futures = pool.invokeAll(attempts, 20, TimeUnit.SECONDS);
      int successes = 0;
      for (var future : futures) if (future.get(1, TimeUnit.SECONDS)) successes++;
      assertThat(successes).isEqualTo(1);
      assertThat(count("approval_decision", "reservation_id", reservation)).isEqualTo(1);
      assertThat(auditCount()).isEqualTo(1); assertThat(requestCount()).isEqualTo(1);
      assertThat(events.events).hasSize(1);
      assertThat(fixtures.reservationStatus(reservation)).isIn("CONFIRMED", "REJECTED");
    } finally { pool.shutdownNow(); }
  }
  boolean concurrentAttempt(UUID actor, ApprovalDecisionType type, CyclicBarrier barrier) throws Exception {
    authenticate(identity(actor, "APPROVER"));
    try {
      barrier.await(5, TimeUnit.SECONDS);
      approvals.decide(actor, new ApprovalCommand(reservation, type, "Concurrent decision", 0));
      return true;
    } catch (ApprovalConflictException conflict) { return false; }
    finally { SecurityContextHolder.clearContext(); }
  }

  @Test void replayWithNewEventAndTimeDoesNotCreateAnotherNotification() {
    var result = decide(ApprovalDecisionType.REJECTED);
    String content = jdbc.queryForObject("SELECT content FROM serms.notification_request WHERE recipient=?", String.class, borrower.toString());
    new TransactionTemplate(transactions).executeWithoutResult(tx -> publisher.publishEvent(
        new ReservationDecided(UUID.randomUUID(), result.decision().approvalDecisionId(), reservation,
            borrower, approver, ApprovalDecisionType.REJECTED, "Changed replay text", result.decision().decidedAt().plusSeconds(60))));
    assertThat(requestCount()).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT content FROM serms.notification_request WHERE recipient=?", String.class, borrower.toString())).isEqualTo(content);
  }

  @Test void revokedApproverIsRejectedAndTheEarlierReservationTransitionRollsBack() {
    jdbc.update("DELETE FROM serms.user_role WHERE user_id=?", approver);
    assertThatThrownBy(() -> decide(ApprovalDecisionType.APPROVED))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    assertUnchanged();
  }

  @Test void persistencePortRequiresTheBusinessTransaction() {
    assertThatThrownBy(() -> decisions.record(reservation, approver, ApprovalDecisionType.APPROVED, null))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertUnchanged();
  }

  @Test void realLoginSessionDeterminesTheRecordedApprover() throws Exception {
    String email = approver + "@example.invalid";
    jdbc.update("UPDATE serms.app_user SET password_hash=? WHERE user_id=?", passwords.encode("UC02-test-password"), approver);
    var login = mvc.perform(post("/api/v1/auth/login").with(anonymous()).with(csrf())
        .contentType("application/json").content("{\"email\":\"" + email + "\",\"password\":\"UC02-test-password\"}"))
        .andExpect(status().isOk()).andReturn();
    var session = (MockHttpSession) login.getRequest().getSession(false);
    assertThat(session).isNotNull();
    mvc.perform(post("/api/v1/approvals/{id}/decision", reservation).session(session).with(csrf())
        .contentType("application/json").content(body(ApprovalDecisionType.APPROVED, 0)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.decision.approverId").value(approver.toString()));
    assertThat(decisions.findByReservationId(reservation).orElseThrow().approverId()).isEqualTo(approver);
  }

  static String body(ApprovalDecisionType type, long version) {
    return "{\"decision\":\"" + type + "\",\"comment\":\"Integration decision\",\"expectedVersion\":" + version + "}";
  }
}
