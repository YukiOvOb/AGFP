package sg.edu.nus.serms.approval.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.context.event.EventListener;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import sg.edu.nus.serms.approval.domain.*;
import sg.edu.nus.serms.approval.service.*;
import sg.edu.nus.serms.approval.service.event.ReservationDecided;

/** Verifies real Spring proxies using the existing test DataSource; ports remain mocks, not DB adapters. */
@SpringBootTest(properties = "serms.approval.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApprovalRuntimeTest.EventsConfiguration.class)
class ApprovalRuntimeTest {
  private static final UUID RESERVATION = new UUID(0, 10), REQUESTER = new UUID(0, 11),
      APPROVER = new UUID(0, 12), DECISION = new UUID(0, 13);
  private static final Instant TIME = Instant.parse("2026-10-06T12:00:00Z");
  @Autowired ApprovalService service;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired EventProbe events;
  @MockitoBean ReservationApprovalPort reservations;
  @MockitoBean ApprovalDecisionPort decisions;

  @TestConfiguration
  static class EventsConfiguration {
    @Bean EventProbe eventProbe() { return new EventProbe(); }
  }

  static class EventProbe {
    final List<Integer> completions = new ArrayList<>();
    final List<ReservationDecided> seen = new ArrayList<>();
    RuntimeException failure;

    void observeCompletion() {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCompletion(int status) { completions.add(status); }
      });
    }

    @EventListener public void on(ReservationDecided event) {
      observeCompletion();
      seen.add(event);
      if (failure != null) throw failure;
    }
  }

  private ApprovalReservationSnapshot view(String status, long version) {
    return new ApprovalReservationSnapshot(RESERVATION, REQUESTER, "Borrower",
        new ApprovalReservationSnapshot.EquipmentSummary(new UUID(0, 14), "EQ-001", "Camera", "Media"),
        TIME.plusSeconds(3600), TIME.plusSeconds(7200), null, status, version, TIME.minusSeconds(3600), false);
  }

  @BeforeEach
  void setup() {
    events.seen.clear();
    events.completions.clear();
    events.failure = null;
    when(reservations.get(RESERVATION)).thenReturn(view("PENDING_APPROVAL", 4L));
    when(reservations.confirm(RESERVATION, 4L)).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      return view("CONFIRMED", 5L);
    });
    when(decisions.record(any(), any(), any(), any())).thenAnswer(call -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      return new RecordedApprovalDecision(DECISION, RESERVATION, APPROVER,
          ApprovalDecisionType.APPROVED, null, TIME);
    });
  }

  private ApprovalCommand command() {
    return new ApprovalCommand(RESERVATION, ApprovalDecisionType.APPROVED, null, 4L);
  }

  @Test
  @WithMockUser(roles = "APPROVER")
  void transitionRecordAndSynchronousEventShareTheSpringTransaction() {
    var result = service.decide(APPROVER, command());
    assertThat(result.decision().decidedAt()).isEqualTo(TIME);
    assertThat(events.seen).hasSize(1);
    assertThat(events.completions).containsExactly(TransactionSynchronization.STATUS_COMMITTED);
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
  }

  @Test
  @WithMockUser(roles = "APPROVER")
  void synchronousListenerFailureRollsBackTheSpringTransaction() {
    events.failure = new IllegalStateException("notification request could not be stored");
    assertThatThrownBy(() -> service.decide(APPROVER, command())).isSameAs(events.failure);
    assertThat(events.completions).containsExactly(TransactionSynchronization.STATUS_ROLLED_BACK);
  }

  @Test
  @WithMockUser(roles = "APPROVER")
  void recordFailureRollsBackBeforeAnyDecisionEvent() {
    var failure = new IllegalStateException("decision could not be stored");
    doAnswer(call -> {
      events.observeCompletion();
      throw failure;
    }).when(decisions).record(any(), any(), any(), any());
    assertThatThrownBy(() -> service.decide(APPROVER, command())).isSameAs(failure);
    assertThat(events.completions).containsExactly(TransactionSynchronization.STATUS_ROLLED_BACK);
    assertThat(events.seen).isEmpty();
  }

  @Test
  void enabledApiPublishesItsRealOpenApiContractWithoutClientActorFields() throws Exception {
    String json = mvc.perform(get("/api/v1/openapi.json")).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    var document = mapper.readTree(json);
    var paths = document.path("paths");
    assertThat(paths.path("/api/v1/approvals/pending").path("get").path("operationId").asText())
        .isEqualTo("listPendingApprovals");
    assertThat(paths.path("/api/v1/approvals/{reservationId}").path("get").path("operationId").asText())
        .isEqualTo("getApprovalReservation");
    assertThat(paths.path("/api/v1/approvals/{reservationId}/decision").path("post").path("operationId").asText())
        .isEqualTo("decideReservation");
    var request = document.path("components").path("schemas").path("DecisionRequest");
    assertThat(request.path("required").toString()).contains("decision", "expectedVersion");
    var fields = request.path("properties");
    assertThat(fields.path("expectedVersion").path("format").asText()).isEqualTo("int64");
    assertThat(fields.has("approverId")).isFalse();
    assertThat(fields.has("requesterId")).isFalse();
    assertThat(document.path("components").path("schemas").path("DecisionResponse")
        .path("properties").path("decidedAt").path("format").asText()).isEqualTo("date-time");
    // Separate evidence file: do not overwrite the default disabled-module frontend API baseline.
    Files.writeString(Path.of("target/approval-openapi.json"), json);
  }
}
