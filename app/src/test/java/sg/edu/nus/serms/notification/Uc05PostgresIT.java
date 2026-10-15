package sg.edu.nus.serms.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import sg.edu.nus.serms.loan.service.LoanService;
import sg.edu.nus.serms.maintenance.domain.MaintenanceAssigned;
import sg.edu.nus.serms.maintenance.domain.MaintenanceCompleted;
import sg.edu.nus.serms.maintenance.domain.MaintenanceStatus;
import sg.edu.nus.serms.maintenance.service.MaintenanceService;
import sg.edu.nus.serms.notification.service.LoanReminderScanner;
import sg.edu.nus.serms.notification.service.CollectionReminderScanner;
import sg.edu.nus.serms.reservation.service.event.ReservationSubmitted;
import sg.edu.nus.serms.reservation.service.event.ReservationCancelled;
import sg.edu.nus.serms.reservation.domain.ReservationStatus;
import sg.edu.nus.serms.reservation.domain.ReservationRequest;
import sg.edu.nus.serms.reservation.service.ReservationService;
import sg.edu.nus.serms.notification.service.NotificationWorker;
import sg.edu.nus.serms.shared.security.SermsUserPrincipal;
import sg.edu.nus.serms.testsupport.SermsFixtures;

/** Real PostgreSQL 18 acceptance of login, event recipients, reminders and inbox visibility. */
@SpringBootTest(properties = {
    "serms.security.secure-cookies=false",
    "server.servlet.session.cookie.secure=false",
    "serms.notifications.scan-ms=3600000",
    "serms.notifications.scan-initial-delay-ms=3600000",
    "serms.notifications.poll-ms=3600000"})
@AutoConfigureMockMvc
class Uc05PostgresIT {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    String url = System.getenv("SERMS_UC05_TEST_JDBC_URL");
    String user = System.getenv("SERMS_UC05_TEST_DB_USER");
    String pass = System.getenv("SERMS_UC05_TEST_DB_PASSWORD");
    if (url == null || url.isBlank()) {
      url = System.getenv("SERMS_UC03_TEST_JDBC_URL");
      user = System.getenv("SERMS_UC03_TEST_DB_USER");
      pass = System.getenv("SERMS_UC03_TEST_DB_PASSWORD");
    }
    if (url == null || url.isBlank()) {
      var pg = new PostgreSQLContainer("postgres:18-alpine").withDatabaseName("serms_test_uc05");
      pg.start(); url = pg.getJdbcUrl(); user = pg.getUsername(); pass = pg.getPassword();
    } else if (!url.matches("jdbc:postgresql://[^/]+/serms_test_uc0[35][a-z0-9_]*(\\?.*)?")) {
      throw new IllegalStateException("UC05 requires an isolated serms_test_uc05 or serms_test_uc03 database");
    }
    String finalUrl = url, finalUser = user, finalPass = pass;
    properties.add("spring.datasource.url", () -> finalUrl);
    properties.add("spring.datasource.username", () -> finalUser);
    properties.add("spring.datasource.password", () -> finalPass);
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired MockMvc mvc;
  @Autowired PasswordEncoder passwords;
  @Autowired LoanService loans;
  @Autowired ReservationService reservations;
  @Autowired MaintenanceService maintenance;
  @Autowired LoanReminderScanner scanner;
  @Autowired CollectionReminderScanner collectionScanner;
  @Autowired NotificationWorker worker;
  @Autowired PlatformTransactionManager manager;
  @Autowired org.springframework.context.ApplicationEventPublisher events;
  @MockitoBean(name = "clock") Clock clock;

  private SermsFixtures fixtures;
  private Instant now;

  @BeforeEach
  void setup() {
    fixtures = new SermsFixtures(jdbc);
    now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    when(clock.instant()).thenReturn(now);
    SecurityContextHolder.clearContext();
  }

  private UUID user(String role) {
    UUID id = fixtures.user(role);
    jdbc.update("UPDATE serms.app_user SET password_hash=? WHERE user_id=?",
        passwords.encode("UC05-test-password"), id);
    return id;
  }

  private void asCustodian(UUID id) { asRole(id, "CUSTODIAN"); }

  private void asRole(UUID id, String role) {
    var principal = new SermsUserPrincipal(id, id + "@example.invalid", role, Set.of(role));
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
  }

  private Cookie csrfCookie() throws Exception {
    return mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isNoContent())
        .andReturn().getResponse().getCookie("XSRF-TOKEN");
  }

  @Test
  void fiveRealAccountsLoginRotateSessionAndLogoutWithoutImplicitAdminApproval() throws Exception {
    for (String role : new String[] {"BORROWER", "APPROVER", "CUSTODIAN", "MAINTAINER", "ADMIN"}) {
      UUID id = user(role);
      var old = new MockHttpSession();
      String previousId = old.getId();
      Cookie token = csrfCookie();
      var result = mvc.perform(post("/api/v1/auth/login").with(anonymous()).session(old)
              .cookie(token).header("X-XSRF-TOKEN", token.getValue())
              .contentType("application/json")
              .content("{\"email\":\"" + id + "@example.invalid\",\"password\":\"UC05-test-password\"}"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(id.toString()))
          .andExpect(jsonPath("$.roles[0]").value(role)).andReturn();
      var session = (MockHttpSession) result.getRequest().getSession(false);
      assertThat(session.getId()).isNotEqualTo(previousId);
      mvc.perform(get("/api/v1/auth/me").session(session))
          .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(id.toString()));
      if (role.equals("ADMIN")) {
        mvc.perform(get("/api/v1/approvals/pending").session(session))
            .andExpect(status().isForbidden());
      }
      Cookie logoutToken = csrfCookie();
      mvc.perform(post("/api/v1/auth/logout").session(session)
              .cookie(logoutToken).header("X-XSRF-TOKEN", logoutToken.getValue()))
          .andExpect(status().isNoContent());
      assertThat(session.isInvalid()).isTrue();
    }
    mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/auth/login").contentType("application/json")
        .content("{\"email\":\"nobody@example.invalid\",\"password\":\"x\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void realApprovalDecisionReachesRequesterInboxAndSupportsReadState() throws Exception {
    UUID approver = user("APPROVER"), approvedRequester = user("BORROWER");
    UUID rejectedRequester = user("BORROWER"), otherBorrower = user("BORROWER");
    UUID equipment = fixtures.equipment("UC05 approval camera", "Camera", "Desk", true);
    UUID approved = fixtures.reservation(approvedRequester, equipment, now.plusSeconds(3600),
        now.plusSeconds(7200), "PENDING_APPROVAL");
    UUID rejected = fixtures.reservation(rejectedRequester, equipment, now.plusSeconds(10800),
        now.plusSeconds(14400), "PENDING_APPROVAL");
    MockHttpSession approverSession = login(approver);
    Cookie token = csrfCookie();
    mvc.perform(post("/api/v1/approvals/" + approved + "/decision")
            .session(approverSession).cookie(token).header("X-XSRF-TOKEN", token.getValue())
            .contentType("application/json")
            .content("{\"decision\":\"APPROVED\",\"expectedVersion\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reservation.status").value("CONFIRMED"))
        .andExpect(jsonPath("$.decision.approverId").value(approver.toString()));
    token = csrfCookie();
    mvc.perform(post("/api/v1/approvals/" + rejected + "/decision")
            .session(approverSession).cookie(token).header("X-XSRF-TOKEN", token.getValue())
            .contentType("application/json")
            .content("{\"decision\":\"REJECTED\",\"comment\":\"Unavailable\",\"expectedVersion\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reservation.status").value("REJECTED"));
    assertThat(count(approvedRequester, "BUSINESS_EVENT")).isEqualTo(1);
    assertThat(count(rejectedRequester, "BUSINESS_EVENT")).isEqualTo(1);
    assertThat(count(approver, "BUSINESS_EVENT")).isZero();
    worker.deliverPending();

    MockHttpSession approvedSession = login(approvedRequester);
    mvc.perform(get("/api/v1/notifications").session(approvedSession))
        .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].content").value(
            "Your reservation for UC05 approval camera has been confirmed."))
        .andExpect(jsonPath("$.content[0].readAt").doesNotExist());
    mvc.perform(get("/api/v1/notifications/unread-count").session(approvedSession))
        .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1));
    String notificationId = jdbc.queryForObject("""
        SELECT id FROM serms.notification_request
        WHERE recipient=? AND content LIKE ? AND status='DELIVERED'
        """, String.class, approvedRequester.toString(), "%UC05 approval camera%");
    MockHttpSession otherSession = login(otherBorrower);
    mvc.perform(get("/api/v1/notifications").session(otherSession))
        .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    token = csrfCookie();
    mvc.perform(post("/api/v1/notifications/" + notificationId + "/read")
            .session(otherSession).cookie(token).header("X-XSRF-TOKEN", token.getValue()))
        .andExpect(status().isNotFound());
    mvc.perform(post("/api/v1/notifications/" + notificationId + "/read")
            .session(approvedSession))
        .andExpect(status().isForbidden());
    token = csrfCookie();
    mvc.perform(post("/api/v1/notifications/" + notificationId + "/read")
            .session(approvedSession).cookie(token).header("X-XSRF-TOKEN", token.getValue()))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/notifications/unread-count").session(approvedSession))
        .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(0));
    mvc.perform(get("/api/v1/notifications").session(approvedSession))
        .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].readAt").exists());
    MockHttpSession rejectedSession = login(rejectedRequester);
    mvc.perform(get("/api/v1/notifications").session(rejectedSession))
        .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].content").value(
            "Your reservation for UC05 approval camera was rejected. Reason: Unavailable"));
    mvc.perform(get("/api/v1/notifications/unread-count").session(rejectedSession))
        .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1));
  }

  private MockHttpSession login(UUID id) throws Exception {
    SecurityContextHolder.clearContext();
    Cookie token = csrfCookie();
    return (MockHttpSession) mvc.perform(post("/api/v1/auth/login").with(anonymous())
            .cookie(token).header("X-XSRF-TOKEN", token.getValue())
            .contentType("application/json")
            .content("{\"email\":\"" + id
                + "@example.invalid\",\"password\":\"UC05-test-password\"}"))
        .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
  }

  @Test
  void persistedLoanEventsAndMaintenanceAssignmentReachOnlyTheirRecipients() throws Exception {
    UUID borrower = user("BORROWER"), custodian = user("CUSTODIAN"), technician = user("MAINTAINER");
    UUID equipment = fixtures.equipment("UC05 camera", "Camera", "Desk", false);
    UUID reservation = fixtures.reservation(borrower, equipment, now.minusSeconds(60),
        now.plusSeconds(3600), "CONFIRMED");
    asCustodian(custodian);
    var loan = loans.checkout(reservation, borrower, 0, true);
    assertThat(count(borrower, "BUSINESS_EVENT")).isEqualTo(1);
    loans.receive(loan.loanId(), 0, "GOOD", null);
    assertThat(count(borrower, "BUSINESS_EVENT")).isEqualTo(2);
    assertThat(count(custodian, "BUSINESS_EVENT")).isZero();
    new TransactionTemplate(manager).executeWithoutResult(status ->
        events.publishEvent(new MaintenanceAssigned(UUID.randomUUID(), equipment, technician,
            custodian, now)));
    assertThat(count(technician, "BUSINESS_EVENT")).isEqualTo(1);
    worker.deliverPending();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM serms.notification_request WHERE recipient=? AND status='DELIVERED'",
        Integer.class, borrower.toString())).isEqualTo(2);
    mvc.perform(get("/api/v1/notifications").session(login(borrower)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
    mvc.perform(get("/api/v1/notifications").session(login(technician)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(get("/api/v1/notifications").session(login(custodian)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void maintenanceCompletionNotifiesReporterOnceForBothTerminalOutcomes() throws Exception {
    UUID reporter = user("BORROWER"), custodian = user("CUSTODIAN"), technician = user("MAINTAINER");
    for (MaintenanceStatus outcome : new MaintenanceStatus[] {
        MaintenanceStatus.RESOLVED, MaintenanceStatus.UNREPAIRABLE}) {
      UUID equipment = fixtures.equipment("UC05 maintenance " + outcome, "Camera", "Desk", false);
      asRole(reporter, "BORROWER");
      var reported = maintenance.reportFault(equipment, reporter, "Lens fault");
      asCustodian(custodian);
      maintenance.assignTechnician(reported.maintenanceCaseId(), technician, custodian);
      asRole(technician, "MAINTAINER");
      maintenance.startMaintenance(reported.maintenanceCaseId(), technician);
      var completed = outcome == MaintenanceStatus.RESOLVED
          ? maintenance.resolve(reported.maintenanceCaseId(), "Replaced lens", technician)
          : maintenance.markUnrepairable(reported.maintenanceCaseId(), "Parts unavailable", technician);
      var duplicate = new MaintenanceCompleted(completed.maintenanceCaseId(), equipment,
          technician, technician, outcome, completed.resolvedAt());
      new TransactionTemplate(manager).executeWithoutResult(status -> events.publishEvent(duplicate));
      assertThat(jdbc.queryForObject("""
          SELECT count(*) FROM serms.notification_request
          WHERE recipient=? AND content LIKE ? AND type='BUSINESS_EVENT'
          """, Integer.class, reporter.toString(),
          "%UC05 maintenance " + outcome + "%")).isEqualTo(1);
    }
    assertThat(count(reporter, "BUSINESS_EVENT")).isEqualTo(2);
    assertThat(count(technician, "BUSINESS_EVENT")).isEqualTo(2);
    assertThat(count(custodian, "BUSINESS_EVENT")).isZero();
    worker.deliverPending();
    assertThat(jdbc.queryForObject("""
        SELECT count(*) FROM serms.notification_request
        WHERE recipient=? AND status='DELIVERED' AND content LIKE 'Maintenance for %'
        """, Integer.class, reporter.toString())).isEqualTo(2);
    mvc.perform(get("/api/v1/notifications").session(login(reporter)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
    mvc.perform(get("/api/v1/notifications").session(login(technician)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
    mvc.perform(get("/api/v1/notifications").session(login(custodian)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void actualLoanScanDeduplicatesAndReturnCancelsUnsentOverdueReminder() {
    UUID borrower = user("BORROWER"), custodian = user("CUSTODIAN");
    UUID equipment = fixtures.equipment("UC05 overdue", "Camera", "Desk", false);
    Instant due = now.plusSeconds(25 * 3600);
    UUID reservation = fixtures.reservation(borrower, equipment, now.minusSeconds(60), due, "CONFIRMED");
    asCustodian(custodian);
    var loan = loans.checkout(reservation, borrower, 0, true);
    long reminderId = jdbc.queryForObject("SELECT reminder_id FROM serms.loan WHERE loan_id=?", Long.class, loan.loanId());
    scanner.scan();
    assertThat(reminderCount(reminderId)).isZero();
    when(clock.instant()).thenReturn(due.minusSeconds(86400));
    scanner.scan();
    scanner.scan();
    assertThat(reminderCount(reminderId)).isEqualTo(1);
    worker.deliverPending();
    assertThat(jdbc.queryForObject("SELECT status FROM serms.notification_request WHERE loan_id=? AND type='DUE_SOON'",
        String.class, reminderId)).isEqualTo("DELIVERED");
    when(clock.instant()).thenReturn(due.plusSeconds(1));
    scanner.scan();
    scanner.scan();
    assertThat(reminderCount(reminderId)).isEqualTo(2);
    loans.receive(loan.loanId(), 0, "GOOD", null);
    worker.deliverPending();
    assertThat(jdbc.queryForObject("SELECT status FROM serms.notification_request WHERE loan_id=? AND type='OVERDUE'",
        String.class, reminderId)).isEqualTo("CANCELLED");
    scanner.scan();
    assertThat(reminderCount(reminderId)).isEqualTo(2);
  }


  @Test
  void reservationEventsAndCollectionReminderUsePersistedRecipientsAndCancelPendingReminder() throws Exception {
    UUID borrower=user("BORROWER"), approver=user("APPROVER"), other=user("BORROWER");
    UUID equipment=fixtures.equipment("Collection camera", "Camera", "Desk", true);
    Instant start=now.plusSeconds(3600), end=start.plusSeconds(3600);
    UUID reservation=fixtures.reservation(borrower,equipment,start,end,"PENDING_APPROVAL");
    var submitted=new ReservationSubmitted(UUID.randomUUID(),reservation,borrower,equipment,start,end,
        ReservationStatus.PENDING_APPROVAL);
    new TransactionTemplate(manager).executeWithoutResult(status->{
      events.publishEvent(submitted); events.publishEvent(submitted);
    });
    assertThat(count(approver,"BUSINESS_EVENT")).isEqualTo(1);
    assertThat(count(other,"BUSINESS_EVENT")).isZero();
    jdbc.update("UPDATE serms.reservation SET status='CONFIRMED' WHERE reservation_id=?",reservation);
    collectionScanner.scan(); collectionScanner.scan();
    assertThat(count(borrower,"BUSINESS_EVENT")).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT content FROM serms.notification_request WHERE recipient=? AND status='PENDING'",
        String.class,borrower.toString())).contains("Collection camera","SGT");
    jdbc.update("UPDATE serms.reservation SET status='CANCELLED' WHERE reservation_id=?",reservation);
    new TransactionTemplate(manager).executeWithoutResult(status->events.publishEvent(
        new ReservationCancelled(UUID.randomUUID(),reservation,borrower,equipment)));
    assertThat(count(borrower,"BUSINESS_EVENT")).isEqualTo(2);
    worker.deliverPending();
    assertThat(jdbc.queryForObject("SELECT status FROM serms.notification_request WHERE recipient=? AND content LIKE 'Collect %'",
        String.class,borrower.toString())).isEqualTo("CANCELLED");
    mvc.perform(get("/api/v1/notifications").session(login(borrower)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void overdueViewUsesRealLoanAndIsRestrictedToCustodianOrAdmin() throws Exception {
    UUID borrower=user("BORROWER"), custodian=user("CUSTODIAN"), admin=user("ADMIN");
    UUID equipment=fixtures.equipment("Overdue camera", "Camera", "Desk", false);
    UUID reservation=fixtures.reservation(borrower,equipment,now.minusSeconds(7200),now.plusSeconds(3600),"CONFIRMED");
    asCustodian(custodian);
    var loan=loans.checkout(reservation,borrower,0,true);
    when(clock.instant()).thenReturn(now.plusSeconds(7200));
    mvc.perform(get("/api/v1/overdue").session(login(custodian)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].loanId").value(org.hamcrest.Matchers.hasItem(loan.loanId().toString())))
        .andExpect(jsonPath("$.content[*].borrowerName").isNotEmpty())
        .andExpect(jsonPath("$.content[*].equipmentName").value(org.hamcrest.Matchers.hasItem("Overdue camera")));
    mvc.perform(get("/api/v1/overdue").session(login(admin))).andExpect(status().isOk());
    mvc.perform(get("/api/v1/overdue").session(login(borrower))).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/overdue")).andExpect(status().isUnauthorized());
    asCustodian(custodian); loans.receive(loan.loanId(),0,"GOOD",null);
    assertThat(mvc.perform(get("/api/v1/overdue").session(login(custodian)))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
        .doesNotContain(loan.loanId().toString());
  }


  @Test
  void overdueSessionMustStillHaveActiveDatabaseRole() throws Exception {
    UUID custodian=user("CUSTODIAN"), admin=user("ADMIN");
    MockHttpSession custodianSession=login(custodian), adminSession=login(admin);
    mvc.perform(get("/api/v1/overdue").session(custodianSession)).andExpect(status().isOk());
    mvc.perform(get("/api/v1/overdue").session(adminSession)).andExpect(status().isOk());
    jdbc.update("UPDATE serms.app_user SET account_status='DISABLED' WHERE user_id=?",custodian);
    // Confirmed Identity contract: disabled old sessions expire globally before module authorization.
    mvc.perform(get("/api/v1/overdue").session(custodianSession)).andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.type").value("/problems/unauthenticated"));
    assertThat(custodianSession.isInvalid()).isTrue();
    jdbc.update("DELETE FROM serms.user_role WHERE user_id=?",admin);
    // Direct fixture-only role deletion without a version bump still exercises the module's DB guard.
    mvc.perform(get("/api/v1/overdue").session(adminSession)).andExpect(status().isForbidden());
  }

  @Test
  void checkoutCancelsCommittedButUnsentCollectionNotice() {
    UUID borrower=user("BORROWER"), custodian=user("CUSTODIAN");
    UUID equipment=fixtures.equipment("Collected camera", "Camera", "Desk", false);
    Instant start=now.plusSeconds(3600);
    UUID reservation=fixtures.reservation(borrower,equipment,start,start.plusSeconds(3600),"CONFIRMED");
    collectionScanner.scan();
    assertThat(jdbc.queryForObject("""
        SELECT status FROM serms.notification_request WHERE recipient=? AND content LIKE 'Collect %'
        """,String.class,borrower.toString())).isEqualTo("PENDING");
    when(clock.instant()).thenReturn(start.plusSeconds(1));
    asCustodian(custodian);
    loans.checkout(reservation,borrower,0,true);
    worker.deliverPending();
    assertThat(jdbc.queryForObject("""
        SELECT status FROM serms.notification_request WHERE recipient=? AND content LIKE 'Collect %'
        """,String.class,borrower.toString())).isEqualTo("CANCELLED");
    assertThat(jdbc.queryForObject("""
        SELECT reason FROM serms.notification_request WHERE recipient=? AND content LIKE 'Collect %'
        """,String.class,borrower.toString())).isEqualTo("RESERVATION_COLLECTED");
  }

  @Test
  void realReservationSubmitCancelAndFailureKeepAuditAndNoticesAtomic() {
    UUID borrower=user("BORROWER");
    UUID equipment=fixtures.equipment("Atomic reservation camera", "Camera", "Desk", false);
    asRole(borrower,"BORROWER");
    var request=new ReservationRequest(equipment,now.plusSeconds(3600),now.plusSeconds(7200),"UC05 test");
    var submitted=reservations.submit(borrower,request);
    UUID reservationId=submitted.reservationId();
    assertThat(jdbc.queryForObject("""
        SELECT count(*) FROM serms.audit_log WHERE entity_type='Reservation' AND entity_id=? AND action='RESERVATION_SUBMIT'
        """,Integer.class,reservationId)).isEqualTo(1);
    assertThat(count(borrower,"BUSINESS_EVENT")).isEqualTo(1);
    reservations.cancel(borrower,reservationId);
    assertThat(jdbc.queryForObject("""
        SELECT count(*) FROM serms.audit_log WHERE entity_type='Reservation' AND entity_id=? AND action='RESERVATION_CANCEL'
        """,Integer.class,reservationId)).isEqualTo(1);
    assertThat(count(borrower,"BUSINESS_EVENT")).isEqualTo(2);
    UUID rollbackEquipment=fixtures.equipment("Rolled back camera", "Camera", "Desk", false);
    var rollbackRequest=new ReservationRequest(rollbackEquipment,now.plusSeconds(10800),now.plusSeconds(14400),"rollback");
    int beforeAudit=jdbc.queryForObject("SELECT count(*) FROM serms.audit_log WHERE actor_id=? AND action='RESERVATION_SUBMIT'",Integer.class,borrower);
    int beforeNotice=count(borrower,"BUSINESS_EVENT");
    org.assertj.core.api.Assertions.assertThatThrownBy(() ->
        new TransactionTemplate(manager).executeWithoutResult(status -> {
          reservations.submit(borrower,rollbackRequest);
          throw new IllegalStateException("simulated downstream failure after event publication");
        })).isInstanceOf(IllegalStateException.class);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM serms.reservation WHERE equipment_id=?",Integer.class,rollbackEquipment)).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM serms.audit_log WHERE actor_id=? AND action='RESERVATION_SUBMIT'",Integer.class,borrower)).isEqualTo(beforeAudit);
    assertThat(count(borrower,"BUSINESS_EVENT")).isEqualTo(beforeNotice);
  }

  private int reminderCount(long reminderId) {
    return jdbc.queryForObject("SELECT count(*) FROM serms.notification_request WHERE loan_id=?",
        Integer.class, reminderId);
  }

  private int count(UUID recipient, String type) {
    return jdbc.queryForObject("SELECT count(*) FROM serms.notification_request WHERE recipient=? AND type=?",
        Integer.class, recipient.toString(), type);
  }
}
