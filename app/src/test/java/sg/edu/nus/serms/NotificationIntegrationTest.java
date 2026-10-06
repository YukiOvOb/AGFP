package sg.edu.nus.serms;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import sg.edu.nus.serms.notification.domain.*;
import sg.edu.nus.serms.notification.repository.*;
import sg.edu.nus.serms.notification.service.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationIntegrationTest {
  @Autowired NotificationRequestService requests;
  @Autowired NotificationDeliveryService delivery;
  @Autowired NotificationRepository repository;
  @Autowired NotificationInboxService inbox;
  @Autowired MockMvc mvc;
  @Autowired PlatformTransactionManager manager;
  @Autowired ApplicationEventPublisher events;
  @Autowired JdbcTemplate jdbc;
  @MockitoBean Clock clock;
  @MockitoBean LoanReminderGuard guard;
  static final String ALICE = "00000000-0000-0000-0000-000000000001";
  static final String BOB = "00000000-0000-0000-0000-000000000002";

  static sg.edu.nus.serms.shared.security.SermsUserPrincipal principal(String id) {
    return new sg.edu.nus.serms.shared.security.SermsUserPrincipal(
        UUID.fromString(id), "user@example.com", "User", Set.of("BORROWER"));
  }

  final Instant now = Instant.parse("2026-09-24T10:00:00Z");

  org.springframework.test.web.servlet.request.RequestPostProcessor realCsrf() throws Exception {
    var token =
        mvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    assertThat(token).isNotNull();
    return request -> {
      request.setCookies(token);
      request.addHeader("X-XSRF-TOKEN", token.getValue());
      return request;
    };
  }

  @BeforeEach
  void setup() {
    jdbc.update("DELETE FROM notification_attempt");
    repository.deleteAll();
    when(clock.instant()).thenReturn(now);
    when(guard.check(anyLong(), anyString(), any(), any(), any()))
        .thenReturn(LoanReminderGuard.Result.VALID);
  }

  NotificationRequest reminder() {
    return new NotificationRequest(
        "alice",
        NotificationType.OVERDUE,
        "loan:1",
        "once",
        1L,
        now.minusSeconds(1),
        "Please return the camera.");
  }

  NotificationRequest business(String recipient, String source) {
    return new NotificationRequest(
        recipient, NotificationType.BUSINESS_EVENT, source, "once", null, null, "Equipment update");
  }

  @Test
  void repeatedRequestKeepsIdentityAndOriginalContent() {
    String a = requests.requestOnce(reminder());
    assertThat(requests.requestOnce(reminder())).isEqualTo(a);
    assertThat(repository.count()).isEqualTo(1);
  }

  @Test
  void concurrentRequestsCreateExactlyOneRecord() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(6);
    try {
      List<Callable<String>> tasks = new ArrayList<>();
      for (int i = 0; i < 12; i++) tasks.add(() -> requests.requestOnce(reminder()));
      Set<String> ids = new HashSet<>();
      for (Future<String> result : pool.invokeAll(tasks)) ids.add(result.get(10, TimeUnit.SECONDS));
      assertThat(ids).hasSize(1);
      assertThat(repository.count()).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void producerRollbackAlsoRollsBackNotificationEvent() {
    new TransactionTemplate(manager)
        .executeWithoutResult(
            tx -> {
              events.publishEvent(new NotificationRequested(reminder()));
              tx.setRollbackOnly();
            });
    assertThat(repository.count()).isZero();
  }

  @Test
  void eventRequiresProducerTransaction() {
    assertThatThrownBy(() -> events.publishEvent(new NotificationRequested(reminder())))
        .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
  }

  @Test
  void committedEventBecomesRecoverablePendingRequest() {
    new TransactionTemplate(manager)
        .executeWithoutResult(tx -> events.publishEvent(new NotificationRequested(reminder())));
    assertThat(repository.findAll())
        .singleElement()
        .extracting(Notification::getStatus)
        .isEqualTo(Notification.Status.PENDING);
  }

  @Test
  void deliveryIsIdempotentAndInboxOnlyShowsDelivered() {
    String id = requests.requestOnce(reminder());
    assertThat(inbox.inbox("alice", 0)).isEmpty();
    delivery.deliver(id);
    delivery.deliver(id);
    assertThat(inbox.inbox("alice", 0)).hasSize(1);
    assertThat(repository.findById(id).orElseThrow().getAttempts()).isEqualTo(1);
  }

  @Test
  void concurrentDeliveryMakesOneVisibilityChange() throws Exception {
    String id = requests.requestOnce(business("alice", "event:concurrent"));
    ExecutorService pool = Executors.newFixedThreadPool(4);
    try {
      List<Callable<Void>> tasks = new ArrayList<>();
      for (int i = 0; i < 8; i++)
        tasks.add(
            () -> {
              delivery.deliver(id);
              return null;
            });
      for (var f : pool.invokeAll(tasks)) f.get(10, TimeUnit.SECONDS);
      assertThat(repository.findById(id).orElseThrow().getAttempts()).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void returnedLoanCancelsPendingReminder() {
    when(guard.check(anyLong(), anyString(), any(), any(), any()))
        .thenReturn(LoanReminderGuard.Result.RETURNED);
    String id = requests.requestOnce(reminder());
    delivery.deliver(id);
    var n = repository.findById(id).orElseThrow();
    assertThat(n.getStatus()).isEqualTo(Notification.Status.CANCELLED);
    assertThat(n.getReason()).isEqualTo("RETURNED");
    assertThat(inbox.inbox("alice", 0)).isEmpty();
  }

  @Test
  void changedDueDateCancelsOldReminderButAllowsNewIdentity() {
    when(guard.check(anyLong(), anyString(), any(), any(), any()))
        .thenReturn(LoanReminderGuard.Result.DUE_DATE_CHANGED);
    String id = requests.requestOnce(reminder());
    delivery.deliver(id);
    assertThat(repository.findById(id).orElseThrow().getStatus())
        .isEqualTo(Notification.Status.CANCELLED);
    var r = reminder();
    assertThat(
            requests.requestOnce(
                new NotificationRequest(
                    r.recipient(),
                    r.type(),
                    r.sourceId(),
                    r.period(),
                    r.loanId(),
                    now.plusSeconds(600),
                    r.content())))
        .isNotEqualTo(id);
  }

  @Test
  void unavailableLoanChecksUseBackoffAndTerminalFailure() {
    when(guard.check(anyLong(), anyString(), any(), any(), any()))
        .thenReturn(LoanReminderGuard.Result.UNAVAILABLE);
    String id = requests.requestOnce(reminder());
    delivery.deliver(id);
    delivery.deliver(id);
    var first = repository.findById(id).orElseThrow();
    assertThat(first.getAttempts()).isEqualTo(1);
    assertThat(first.getNextAttemptAt()).isEqualTo(now.plusSeconds(60));
    when(clock.instant()).thenReturn(now.plusSeconds(60));
    delivery.deliver(id);
    when(clock.instant()).thenReturn(now.plusSeconds(120));
    delivery.deliver(id);
    delivery.deliver(id);
    var last = repository.findById(id).orElseThrow();
    assertThat(last.getStatus()).isEqualTo(Notification.Status.FAILED);
    assertThat(last.getAttempts()).isEqualTo(3);
    assertThat(last.getNextAttemptAt()).isNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification_attempt WHERE notification_id=?",
                Integer.class,
                id))
        .isEqualTo(3);
  }

  @Test
  void deliveredHistoryIsNotCancelledOnLaterReturn() {
    String id = requests.requestOnce(reminder());
    delivery.deliver(id);
    when(guard.check(anyLong(), anyString(), any(), any(), any()))
        .thenReturn(LoanReminderGuard.Result.RETURNED);
    delivery.deliver(id);
    assertThat(repository.findById(id).orElseThrow().getStatus())
        .isEqualTo(Notification.Status.DELIVERED);
  }

  @Test
  void pageRequiresAuthenticationAndEscapesContent() throws Exception {
    mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
    String id =
        requests.requestOnce(
            new NotificationRequest(
                ALICE,
                NotificationType.BUSINESS_EVENT,
                "escape",
                "once",
                null,
                null,
                "<script>alert(1)</script>"));
    delivery.deliver(id);
    mvc.perform(get("/api/v1/notifications").with(user(principal(ALICE))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].content").value("<script>alert(1)</script>"));
    mvc.perform(get("/api/v1/notifications").with(user(principal(BOB))))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("alert(1)"))));
  }

  @Test
  void markReadEnforcesCsrfAndOwnership() throws Exception {
    String id = requests.requestOnce(business(ALICE, "event:read"));
    delivery.deliver(id);
    mvc.perform(post("/api/v1/notifications/" + id + "/read").with(user(principal(ALICE))))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/notifications/" + id + "/read")
                .with(user(principal(BOB)))
                .with(realCsrf()))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/v1/notifications/" + id + "/read")
                .with(user(principal(ALICE)))
                .with(realCsrf()))
        .andExpect(status().isNoContent());
    Instant read = repository.findById(id).orElseThrow().getReadAt();
    assertThat(read).isEqualTo(now);
    when(clock.instant()).thenReturn(now.plusSeconds(10));
    inbox.markRead(id, ALICE);
    assertThat(repository.findById(id).orElseThrow().getReadAt()).isEqualTo(read);
  }

  @Test
  void recipientMatchingIsCaseSensitive() {
    String id = requests.requestOnce(business("Alice", "event:case"));
    delivery.deliver(id);
    assertThat(inbox.inbox("alice", 0)).isEmpty();
    assertThat(inbox.inbox("Alice", 0)).hasSize(1);
  }

  @Test
  void csrfBootstrapIsPublic() throws Exception {
    mvc.perform(get("/api/v1/auth/csrf"))
        .andExpect(status().isNoContent())
        .andExpect(cookie().exists("XSRF-TOKEN"));
  }

  @Test
  void healthIsPublicButIdentityIsProtected() throws Exception {
    mvc.perform(get("/api/v1/health")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }

  @Test
  void paginationUnreadAndInvalidInputs() throws Exception {
    for (int i = 0; i < 3; i++)
      delivery.deliver(requests.requestOnce(business(ALICE, "page:" + i)));
    requests.requestOnce(business(ALICE, "pending"));
    mvc.perform(get("/api/v1/notifications").param("size", "2").with(user(principal(ALICE))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.totalElements").value(3));
    mvc.perform(get("/api/v1/notifications/unread-count").with(user(principal(ALICE))))
        .andExpect(jsonPath("$.count").value(3));
    for (String value : List.of("0", "101", "not-a-number"))
      mvc.perform(get("/api/v1/notifications").param("size", value).with(user(principal(ALICE))))
          .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/v1/notifications")
                .param("sort", "recipient,asc")
                .with(user(principal(ALICE))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void exportsActualOpenApi() throws Exception {
    String json =
        mvc.perform(get("/api/v1/openapi.json"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/notifications']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/auth/csrf'].get.parameters").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();
    java.nio.file.Files.writeString(java.nio.file.Path.of("target/openapi.json"), json);
  }

  @Test
  @org.springframework.security.test.context.support.WithMockUser(username = BOB)
  void serviceRejectsAnotherUsersIdentity() {
    assertThatThrownBy(() -> inbox.page(UUID.fromString(ALICE), 0, 20, "deliveredAt,desc"))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    assertThatThrownBy(() -> inbox.unread(UUID.fromString(ALICE)))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    assertThatThrownBy(() -> inbox.markReadForUser(UUID.randomUUID(), UUID.fromString(ALICE)))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }
}
