package sg.edu.nus.serms.loan;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import sg.edu.nus.serms.loan.service.*;
import sg.edu.nus.serms.loan.domain.*;
import sg.edu.nus.serms.testsupport.SermsFixtures;
import sg.edu.nus.serms.shared.security.SermsUserPrincipal;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.mockito.Mockito.when;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;

/** Full application: real Flyway V001-V007, security, UC03, UC04 and notification bridge. */
@SpringBootTest(properties={"serms.notifications.worker-enabled=false", "springdoc.api-docs.enabled=true",
    "serms.security.secure-cookies=false", "server.servlet.session.cookie.secure=false"})
@AutoConfigureMockMvc
@Import(LoanPostgresIT.EventsConfig.class)
class LoanPostgresIT {
  @TestConfiguration static class EventsConfig {
    @Bean FailureListener failureListener() { return new FailureListener(); }
  }
  static class FailureListener {
    volatile boolean fail;
    @EventListener void onLoan(LoanEvent e) { if (fail) throw new IllegalStateException("Simulated downstream failure"); }
  }
  @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
    String url = System.getenv("SERMS_UC03_TEST_JDBC_URL");
    String user = System.getenv("SERMS_UC03_TEST_DB_USER");
    String pass = System.getenv("SERMS_UC03_TEST_DB_PASSWORD");
    if (url == null || url.isBlank()) {
      var pg = new PostgreSQLContainer("postgres:18-alpine").withDatabaseName("serms_test_uc03");
      pg.start(); url = pg.getJdbcUrl(); user = pg.getUsername(); pass = pg.getPassword();
    } else if (!url.matches("jdbc:postgresql://[^/]+/serms_test_uc03[a-z0-9_]*(\\?.*)?")) {
      throw new IllegalStateException("UC03 requires a fresh isolated serms_test_uc03 database");
    }
    String finalUrl=url, finalUser=user, finalPass=pass;
    p.add("spring.datasource.url", () -> finalUrl);
    p.add("spring.datasource.username", () -> finalUser);
    p.add("spring.datasource.password", () -> finalPass);
  }
  @Autowired JdbcTemplate jdbc;
  @Autowired LoanService service;
  @Autowired MockMvc mvc;
  @Autowired PlatformTransactionManager transactions;
  @Autowired FailureListener listener;
  @Autowired sg.edu.nus.serms.approval.service.ApprovalService approvals;
  @Autowired sg.edu.nus.serms.notification.service.LoanReminderGuard guard;
  @Autowired sg.edu.nus.serms.integration.notifications.LoanNotificationRequestFactory reminders;
  @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
  @Autowired org.springframework.core.env.Environment environment;
  @MockitoBean(name="clock") Clock clock;
  SermsFixtures fixtures;
  UUID borrower, custodian, equipment, reservation;
  Instant start,end;
  SermsUserPrincipal operator;

  @BeforeEach void setup() {
    when(clock.instant()).thenReturn(Instant.now().truncatedTo(ChronoUnit.MICROS));
    fixtures = new SermsFixtures(jdbc);
    borrower=fixtures.user("BORROWER"); custodian=fixtures.user("CUSTODIAN");
    equipment=fixtures.equipment("UC03 camera", "Camera", "Desk",false);
    start=Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MICROS);
    end=start.plusSeconds(3600);
    reservation=fixtures.reservation(borrower,equipment,start,end,"CONFIRMED");
    operator=new SermsUserPrincipal(custodian,custodian+"@example.invalid","Custodian",Set.of("CUSTODIAN"));
    authenticate(operator);
  }
  @AfterEach void clear() { listener.fail=false; SecurityContextHolder.clearContext(); }
  void authenticate(SermsUserPrincipal p) {
    SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p,null,p.getAuthorities()));
  }
  LoanView checkout() { return service.checkout(reservation,borrower,0,true); }
  String state() { return jdbc.queryForObject("SELECT status FROM serms.equipment WHERE equipment_id=?",String.class,equipment); }
  long count(String table,String key,UUID id) { return jdbc.queryForObject("SELECT count(*) FROM serms."+table+" WHERE "+key+"=?",Long.class,id); }

  @Test void checkoutAndGoodReturnAreAtomicAndLeaveReservationSlotOccupied() {
    var loan=checkout();
    assertThat(state()).isEqualTo("ON_LOAN");
    assertThat(jdbc.queryForObject("SELECT status FROM serms.reservation WHERE reservation_id=?",String.class,reservation)).isEqualTo("FULFILLED");
    var returned=service.receive(loan.loanId(),0,"GOOD",null);
    assertThat(returned.status()).isEqualTo("RETURNED");
    assertThat(returned.version()).isEqualTo(1);
    assertThat(state()).isEqualTo("AVAILABLE");
    assertThat(count("audit_log","entity_id",loan.loanId())).isEqualTo(2);
    assertThatThrownBy(() -> service.receive(loan.loanId(),0,"GOOD",null)).isInstanceOf(LoanConflictException.class);
    assertThatThrownBy(this::checkout).isInstanceOf(LoanConflictException.class);
  }
  @Test void damagedReturnCreatesLinkedCaseAndMaintenanceAudit() {
    var loan=checkout();
    service.receive(loan.loanId(),0,"DAMAGED","Lens cracked");
    assertThat(state()).isEqualTo("UNDER_MAINTENANCE");
    assertThat(count("maintenance_case","loan_id",loan.loanId())).isEqualTo(1);
    UUID caseId=jdbc.queryForObject("SELECT maintenance_case_id FROM serms.maintenance_case WHERE loan_id=?",UUID.class,loan.loanId());
    assertThat(count("audit_log","entity_id",caseId)).isEqualTo(1);
    assertThat(jdbc.queryForMap("SELECT fault_category, priority FROM serms.maintenance_case WHERE maintenance_case_id=?",caseId))
        .containsEntry("fault_category","PHYSICAL_DAMAGE").containsEntry("priority","HIGH");
  }
  @Test void validationRejectsIllegalHandoverWithoutWrites() {
    assertThatThrownBy(() -> service.checkout(reservation,borrower,0,false)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.checkout(reservation,UUID.randomUUID(),0,true)).isInstanceOf(LoanConflictException.class);
    assertThatThrownBy(() -> service.checkout(reservation,borrower,1,true)).isInstanceOf(LoanConflictException.class);
    fixtures.equipmentStatus(equipment,"UNDER_MAINTENANCE");
    assertThatThrownBy(this::checkout).isInstanceOf(LoanConflictException.class);
    assertThat(count("loan","reservation_id",reservation)).isZero();
  }
  @Test void pendingCancelledAndEarlyReservationsCannotBeCollected() {
    var restricted=fixtures.equipment("Restricted","Camera","Desk",true);
    var pending=fixtures.reservation(borrower,restricted,start,end,"PENDING_APPROVAL");
    assertThatThrownBy(() -> service.checkout(pending,borrower,0,true)).isInstanceOf(LoanConflictException.class);
    jdbc.update("UPDATE serms.reservation SET status='CANCELLED' WHERE reservation_id=?",reservation);
    assertThatThrownBy(() -> service.checkout(reservation,borrower,1,true)).isInstanceOf(LoanConflictException.class);
    var futureItem=fixtures.equipment("Future","Camera","Desk",false);
    var future=fixtures.reservation(borrower,futureItem,end,end.plusSeconds(3600),"CONFIRMED");
    assertThatThrownBy(() -> service.checkout(future,borrower,0,true)).isInstanceOf(LoanConflictException.class);
  }
  @Test void disabledBorrowerAndRevokedCustodianAreRejected() {
    jdbc.update("UPDATE serms.app_user SET account_status='DISABLED' WHERE user_id=?",borrower);
    assertThatThrownBy(this::checkout).isInstanceOf(LoanConflictException.class);
    jdbc.update("UPDATE serms.app_user SET account_status='ACTIVE' WHERE user_id=?",borrower);
    jdbc.update("DELETE FROM serms.user_role WHERE user_id=?",custodian);
    assertThatThrownBy(this::checkout).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }
  @Test void existingMaintenanceAndRetiredStateSurviveGoodReceipt() {
    var loan=checkout();
    fixtures.openMaintenanceCase(equipment,custodian);
    service.receive(loan.loanId(),0,"GOOD",null);
    assertThat(state()).isEqualTo("UNDER_MAINTENANCE");
  }
  @Test void retiredDamagedReceiptIsAllowedWithoutRestoringEquipment() {
    var loan=checkout(); fixtures.equipmentStatus(equipment,"RETIRED");
    service.receive(loan.loanId(),0,"DAMAGED","Damaged before retirement");
    assertThat(state()).isEqualTo("RETIRED");
    assertThat(count("maintenance_case","loan_id",loan.loanId())).isEqualTo(1);
    assertThat(jdbc.queryForMap("SELECT fault_category, priority FROM serms.maintenance_case WHERE loan_id=?",loan.loanId()))
        .containsEntry("fault_category","PHYSICAL_DAMAGE").containsEntry("priority","HIGH");
  }
  @Test void returnRequiresDamageDescriptionAndCurrentVersion() {
    var loan=checkout();
    assertThatThrownBy(() -> service.receive(loan.loanId(),0,"DAMAGED"," ")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.receive(loan.loanId(),1,"GOOD",null)).isInstanceOf(LoanConflictException.class);
    assertThat(state()).isEqualTo("ON_LOAN");
  }
  @Test void concurrentCheckoutsHaveOneWinner() throws Exception {
    var pool=Executors.newFixedThreadPool(2); var barrier=new CyclicBarrier(2);
    Callable<Boolean> attempt=() -> { authenticate(operator); try {
      barrier.await(10,TimeUnit.SECONDS); checkout(); return true;
    } catch(LoanConflictException e) { return false; } finally { SecurityContextHolder.clearContext(); }};
    try { var a=pool.submit(attempt); var b=pool.submit(attempt);
      assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
      assertThat(count("loan","reservation_id",reservation)).isEqualTo(1);
    } finally { pool.shutdownNow(); }
  }
  @Test void rollbackRemovesLoanEquipmentChangeAndAudit() {
    new TransactionTemplate(transactions).executeWithoutResult(tx -> { checkout(); tx.setRollbackOnly(); });
    assertThat(state()).isEqualTo("AVAILABLE");
    assertThat(count("loan","reservation_id",reservation)).isZero();
  }
  @Test void borrowerSeesOnlyOwnLoansAndCannotMutate() throws Exception {
    checkout();
    var other=fixtures.user("BORROWER");
    authenticate(new SermsUserPrincipal(other,"other@example.invalid","Other",Set.of("BORROWER")));
    assertThat(service.list(true,0,100)).isEmpty();
    assertThatThrownBy(this::checkout).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    mvc.perform(get("/api/v1/loans").with(anonymous())).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/loans").with(user(operator)).contentType("application/json").content("{}"))
        .andExpect(status().isForbidden());
  }
  @Test void apiValidatesVersionAndIdentityAndReturnsConflict() throws Exception {
    String body="{\"reservationId\":\""+reservation+"\",\"borrowerId\":\""+borrower+"\",\"expectedVersion\":0,\"identityConfirmed\":true}";
    mvc.perform(post("/api/v1/loans").with(user(operator)).with(csrf()).contentType("application/json").content(body))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE"));
    mvc.perform(post("/api/v1/loans").with(user(operator)).with(csrf()).contentType("application/json").content(body))
        .andExpect(status().isConflict());
    mvc.perform(post("/api/v1/loans").with(user(operator)).with(csrf()).contentType("application/json").content(body.replace("\"expectedVersion\":0","\"expectedVersion\":null")))
        .andExpect(status().isBadRequest());
    for (String invalid : List.of("1.5","\"0\"","9223372036854775808","-1")) {
      mvc.perform(post("/api/v1/loans").with(user(operator)).with(csrf()).contentType("application/json")
          .content(body.replace("\"expectedVersion\":0","\"expectedVersion\":"+invalid)))
          .andExpect(status().isBadRequest());
    }
  }
  @Test void schemaAndIdentityBridgeArePresent() {
    assertThat(environment.getActiveProfiles()).isEmpty();
    assertThat(environment.getDefaultProfiles()).contains("serms-postgres");
    assertThat(environment.getProperty("spring.datasource.driver-class-name")).isEqualTo("org.postgresql.Driver");
    assertThat(environment.getProperty("spring.flyway.locations")).isEqualTo("classpath:db/migration");
    assertThat(environment.getProperty("serms.approval.enabled")).isEqualTo("true");
    assertThat(jdbc.queryForObject("SELECT max(version) FROM serms.schema_version",Integer.class)).isEqualTo(7);
    assertThat(jdbc.queryForObject("SELECT notification_principal FROM serms.app_user WHERE user_id=?",String.class,custodian)).isEqualTo(custodian.toString());
    assertThat(service.checkoutQueue(0,100)).anyMatch(r -> r.reservationId().equals(reservation));
  }
  @Test void expiryBoundaryRejectsCheckoutAndLateDamagedReceiptPreservesBothFacts() {
    when(clock.instant()).thenReturn(end);
    assertThatThrownBy(this::checkout).isInstanceOf(LoanConflictException.class);
    when(clock.instant()).thenReturn(start.plusSeconds(60));
    var loan=checkout();
    when(clock.instant()).thenReturn(end.plusSeconds(1));
    var returned=service.receive(loan.loanId(),0,"DAMAGED","Late and cracked");
    assertThat(returned.overdue()).isTrue();
    assertThat(returned.returnCondition()).isEqualTo("DAMAGED");
    assertThat(state()).isEqualTo("UNDER_MAINTENANCE");
  }
  @Test void failedDownstreamEventRollsBackDamagedReceiptAndMaintenanceCase() {
    var loan=checkout(); listener.fail=true;
    assertThatThrownBy(() -> service.receive(loan.loanId(),0,"DAMAGED","Cracked"))
        .isInstanceOf(IllegalStateException.class);
    assertThat(state()).isEqualTo("ON_LOAN");
    assertThat(count("maintenance_case","loan_id",loan.loanId())).isZero();
    assertThat(count("audit_log","entity_id",loan.loanId())).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT status FROM serms.loan WHERE loan_id=?",String.class,loan.loanId())).isEqualTo("ACTIVE");
  }
  @Test void concurrentReturnsHaveOneWinnerAndOneDamageCase() throws Exception {
    var loan=checkout(); var pool=Executors.newFixedThreadPool(2); var barrier=new CyclicBarrier(2);
    Callable<Boolean> attempt=() -> { authenticate(operator); try {
      barrier.await(10,TimeUnit.SECONDS); service.receive(loan.loanId(),0,"DAMAGED","Cracked"); return true;
    } catch(LoanConflictException e) { return false; } finally { SecurityContextHolder.clearContext(); }};
    try { var a=pool.submit(attempt); var b=pool.submit(attempt);
      assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
      assertThat(count("maintenance_case","loan_id",loan.loanId())).isEqualTo(1);
      assertThat(count("audit_log","entity_id",loan.loanId())).isEqualTo(2);
    } finally { pool.shutdownNow(); }
  }
  @Test void approvalPersistenceAndNotificationJoinRestrictedCheckoutFlow() {
    UUID restricted=fixtures.equipment("Restricted UC03","Camera","Desk",true);
    Instant pickupAt=Instant.now().plusSeconds(60).truncatedTo(ChronoUnit.MICROS);
    UUID pending=fixtures.reservation(borrower,restricted,pickupAt,pickupAt.plusSeconds(3600),"PENDING_APPROVAL");
    UUID approver=fixtures.user("APPROVER");
    authenticate(new SermsUserPrincipal(approver,"approver@example.invalid","Approver",Set.of("APPROVER")));
    approvals.decide(approver,new sg.edu.nus.serms.approval.domain.ApprovalCommand(pending,
        sg.edu.nus.serms.approval.domain.ApprovalDecisionType.APPROVED,null,0));
    assertThat(jdbc.queryForObject("SELECT decision FROM serms.approval_decision WHERE reservation_id=?",String.class,pending)).isEqualTo("APPROVED");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM serms.notification_request WHERE recipient=?",Long.class,borrower.toString())).isEqualTo(1);
    authenticate(operator);
    when(clock.instant()).thenReturn(pickupAt);
    assertThat(service.checkout(pending,borrower,1,true).status()).isEqualTo("ACTIVE");
  }
  @Test void returnedLoanSuppressesPendingOverdueReminderThroughRealGuard() {
    var loan=checkout();
    var template=new TransactionTemplate(transactions);
    var request=template.execute(tx -> reminders.forLoan(loan.loanId(),
        sg.edu.nus.serms.notification.domain.NotificationType.OVERDUE,"day-1","Please return equipment"));
    assertThat(request.recipient()).isEqualTo(borrower.toString());
    var type=sg.edu.nus.serms.notification.domain.NotificationType.OVERDUE;
    var valid=template.execute(tx -> guard.check(request.loanId(),request.recipient(),request.expectedDueAt(),type,end.plusSeconds(1)));
    assertThat(valid).isEqualTo(sg.edu.nus.serms.notification.service.LoanReminderGuard.Result.VALID);
    service.receive(loan.loanId(),0,"GOOD",null);
    var returned=template.execute(tx -> guard.check(request.loanId(),request.recipient(),request.expectedDueAt(),type,end.plusSeconds(1)));
    assertThat(returned).isEqualTo(sg.edu.nus.serms.notification.service.LoanReminderGuard.Result.RETURNED);
  }
  @Test void loanDetailsArePrivateAndOpenApiMatchesImplementedOperations() throws Exception {
    var loan=checkout();
    assertThat(service.get(loan.loanId()).loanId()).isEqualTo(loan.loanId());
    UUID other=fixtures.user("BORROWER");
    authenticate(new SermsUserPrincipal(other,"other@example.invalid","Other",Set.of("BORROWER")));
    assertThatThrownBy(() -> service.get(loan.loanId())).isInstanceOf(sg.edu.nus.serms.shared.domain.RecordNotFoundException.class);
    String json=mvc.perform(get("/api/v1/openapi.json").with(user(operator))).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    assertThat(json).contains("/api/v1/loans","/api/v1/loans/checkouts","/api/v1/loans/{loanId}/return");
    java.nio.file.Files.writeString(java.nio.file.Path.of("target","uc03-openapi.json"),json);
  }
  @Test void realLoginUsesSharedSchemaAndUuidPrincipal() throws Exception {
    jdbc.update("UPDATE serms.app_user SET password_hash=? WHERE user_id=?",passwords.encode("UC03-test-password"),custodian);
    mvc.perform(post("/api/v1/auth/login").with(anonymous()).with(csrf()).contentType("application/json")
        .content("{\"email\":\""+custodian+"@example.invalid\",\"password\":\"UC03-test-password\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(custodian.toString()))
        .andExpect(jsonPath("$.roles[0]").value("CUSTODIAN"));
  }
}
