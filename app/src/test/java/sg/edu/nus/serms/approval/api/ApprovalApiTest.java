package sg.edu.nus.serms.approval.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import sg.edu.nus.serms.approval.domain.ApprovalCommand;
import sg.edu.nus.serms.approval.domain.ApprovalDecisionType;
import sg.edu.nus.serms.approval.service.*;
import sg.edu.nus.serms.approval.service.event.ReservationDecided;
import sg.edu.nus.serms.identity.api.AuthController;
import sg.edu.nus.serms.identity.service.IdentityService;
import sg.edu.nus.serms.shared.config.SecurityConfig;
import sg.edu.nus.serms.shared.domain.RecordNotFoundException;
import sg.edu.nus.serms.shared.security.*;

/** Real controller, policy/service proxies and shared security; only outbound ports are mocked. */
@org.springframework.test.context.ActiveProfiles("test")
@WebMvcTest(controllers = {ApprovalController.class, AuthController.class}, properties = {
    "serms.approval.enabled=true", "serms.security.secure-cookies=false"})
@Import({SecurityConfig.class, IdentityAuthenticationProvider.class, SecurityProblemWriter.class,
    ApprovalConfiguration.class, ApprovalApiTest.EventsConfiguration.class})
class ApprovalApiTest {
  private static final String ROOT = "/api/v1/approvals";
  private static final UUID RESERVATION = new UUID(0, 1), REQUESTER = new UUID(0, 2),
      APPROVER = new UUID(0, 3), DECISION = new UUID(0, 4), EQUIPMENT = new UUID(0, 5);
  private static final Instant DECIDED_AT = Instant.parse("2026-10-06T12:00:00Z");

  @Autowired MockMvc mvc;
  @Autowired ApprovalService service;
  @Autowired DecisionEvents events;
  @MockitoBean ReservationApprovalPort reservations;
  @MockitoBean ApprovalDecisionPort decisions;
  @MockitoBean IdentityService identity;

  @TestConfiguration
  static class EventsConfiguration {
    @Bean DecisionEvents decisionEvents() { return new DecisionEvents(); }
  }

  static class DecisionEvents {
    final List<ReservationDecided> seen = new ArrayList<>();
    @EventListener public void on(ReservationDecided event) { seen.add(event); }
  }

  @BeforeEach
  void setup() {
    when(identity.isSessionCurrent(any(), anyLong())).thenReturn(true);
    events.seen.clear();
    when(reservations.get(RESERVATION)).thenReturn(view("PENDING_APPROVAL", 4L, false, REQUESTER));
    when(reservations.confirm(eq(RESERVATION), anyLong()))
        .thenAnswer(call -> view("CONFIRMED", call.<Long>getArgument(1) + 1, false, REQUESTER));
    when(reservations.reject(eq(RESERVATION), anyLong()))
        .thenAnswer(call -> view("REJECTED", call.<Long>getArgument(1) + 1, false, REQUESTER));
    when(decisions.record(eq(RESERVATION), eq(APPROVER), any(), any()))
        .thenAnswer(call -> recorded(call.getArgument(2), call.getArgument(3)));
    when(decisions.findByReservationId(RESERVATION)).thenReturn(Optional.empty());
  }

  private ApprovalReservationSnapshot view(String status, long version, boolean expired, UUID requester) {
    return new ApprovalReservationSnapshot(RESERVATION, requester, "Borrower",
        new ApprovalReservationSnapshot.EquipmentSummary(EQUIPMENT, "EQ-001", "Camera", "Media"),
        DECIDED_AT.plusSeconds(expired ? -3600 : 3600), DECIDED_AT.plusSeconds(7200),
        "Course project", status, version, DECIDED_AT.minusSeconds(7200), expired);
  }

  @Test void duplicateDecisionUsesFreshSnapshotForContracted409Metadata() throws Exception {
    when(decisions.record(eq(RESERVATION), eq(APPROVER), any(), any()))
        .thenThrow(new ApprovalDecisionAlreadyRecordedException(RESERVATION, new IllegalStateException("test constraint")));
    mvc.perform(decide(approve()))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.type").value("/problems/state-conflict"))
        .andExpect(jsonPath("$.currentStatus").value("PENDING_APPROVAL"))
        .andExpect(jsonPath("$.currentVersion").value(4));
    verify(reservations, times(2)).get(RESERVATION);
    assertThat(events.seen).isEmpty();
  }

  private RecordedApprovalDecision recorded(ApprovalDecisionType type, String comment) {
    return new RecordedApprovalDecision(DECISION, RESERVATION, APPROVER, type, comment, DECIDED_AT);
  }

  private RequestPostProcessor as(String... roles) {
    return user(new SermsUserPrincipal(APPROVER, "approver@example.com", "Approver", Set.of(roles)));
  }

  private RequestPostProcessor realCsrf() throws Exception {
    var token = mvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    assertThat(token).isNotNull();
    return request -> {
      request.setCookies(token);
      request.addHeader("X-XSRF-TOKEN", token.getValue());
      return request;
    };
  }

  private String approve() { return "{\"decision\":\"APPROVED\",\"expectedVersion\":4}"; }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder decide(String body)
      throws Exception {
    return post(ROOT + "/" + RESERVATION + "/decision").with(as("APPROVER")).with(realCsrf())
        .contentType("application/json").content(body);
  }

  @Test
  void unauthenticatedRequestsReturn401WithoutRedirectOrLookup() throws Exception {
    mvc.perform(get(ROOT + "/" + RESERVATION)).andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.type").value("/problems/unauthenticated"));
    mvc.perform(post(ROOT + "/" + RESERVATION + "/decision").with(realCsrf())
        .contentType("application/json").content(approve())).andExpect(status().isUnauthorized());
    verifyNoInteractions(reservations, decisions);
  }

  @ParameterizedTest
  @ValueSource(strings = {"BORROWER", "ADMIN", "CUSTODIAN", "MAINTAINER"})
  void nonApproversCannotListReadOrDecideEvenForAnUnknownId(String role) throws Exception {
    UUID unknown = new UUID(0, 999);
    mvc.perform(get(ROOT + "/pending").with(as(role))).andExpect(status().isForbidden());
    mvc.perform(get(ROOT + "/" + unknown).with(as(role))).andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("/problems/forbidden"));
    mvc.perform(post(ROOT + "/" + unknown + "/decision").with(as(role)).with(realCsrf())
        .contentType("application/json").content(approve())).andExpect(status().isForbidden());
    verifyNoInteractions(reservations, decisions);
    assertThat(events.seen).isEmpty();
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void directServiceCallsAlsoRequireTheExplicitApproverRole() {
    assertThatThrownBy(() -> service.get(RESERVATION)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.findPendingApproval(org.springframework.data.domain.PageRequest.of(0, 20)))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.decide(APPROVER,
        new ApprovalCommand(RESERVATION, ApprovalDecisionType.APPROVED, null, 4L)))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(reservations, decisions);
  }

  @Test
  void missingAndInvalidCsrfAreRejectedBeforeWrites() throws Exception {
    mvc.perform(post(ROOT + "/" + RESERVATION + "/decision").with(as("APPROVER"))
        .contentType("application/json").content(approve())).andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("/problems/csrf"));
    var token = mvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    mvc.perform(post(ROOT + "/" + RESERVATION + "/decision").with(as("APPROVER"))
        .cookie(token).header("X-XSRF-TOKEN", "invalid").contentType("application/json").content(approve()))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.type").value("/problems/csrf"));
    verifyNoInteractions(reservations, decisions);
  }

  @Test
  void pendingListIncludesExpiredItemsAndUsesTheContractDefaultPage() throws Exception {
    var expired = view("PENDING_APPROVAL", 4L, true, REQUESTER);
    when(reservations.findPendingApproval(any())).thenAnswer(call ->
        new PageImpl<>(List.of(expired), call.<Pageable>getArgument(0), 1));
    mvc.perform(get(ROOT + "/pending").with(as("APPROVER"))).andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].expired").value(true))
        .andExpect(jsonPath("$.content[0].equipment.equipmentId").value(EQUIPMENT.toString()))
        .andExpect(jsonPath("$.number").value(0)).andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.totalElements").value(1));
    var page = ArgumentCaptor.forClass(Pageable.class);
    verify(reservations).findPendingApproval(page.capture());
    assertThat(page.getValue().getSort()).isEqualTo(org.springframework.data.domain.Sort.by("startAt").ascending());
  }

  @ParameterizedTest
  @CsvSource({"page,-1", "size,0", "size,101", "sort,name"})
  void invalidOrUnboundedPaginationReturnsFieldErrors(String field, String value) throws Exception {
    mvc.perform(get(ROOT + "/pending").param(field, value).with(as("APPROVER")))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.type").value("/problems/validation"))
        .andExpect(jsonPath("$.errors[0].field").value(field));
    verifyNoInteractions(reservations, decisions);
  }

  @Test
  void detailReturnsPersistedDecisionAndReservationFields() throws Exception {
    when(reservations.get(RESERVATION)).thenReturn(view("CONFIRMED", 5L, false, REQUESTER));
    when(decisions.findByReservationId(RESERVATION)).thenReturn(Optional.of(recorded(ApprovalDecisionType.APPROVED, null)));
    mvc.perform(get(ROOT + "/" + RESERVATION).with(as("APPROVER"))).andExpect(status().isOk())
        .andExpect(jsonPath("$.reservationId").value(RESERVATION.toString()))
        .andExpect(jsonPath("$.status").value("CONFIRMED")).andExpect(jsonPath("$.version").value(5))
        .andExpect(jsonPath("$.decision.approvalId").value(DECISION.toString()))
        .andExpect(jsonPath("$.decision.decidedAt").value(DECIDED_AT.toString()));
  }

  @Test
  void pendingDetailHasNoFabricatedDecision() throws Exception {
    mvc.perform(get(ROOT + "/" + RESERVATION).with(as("APPROVER"))).andExpect(status().isOk())
        .andExpect(jsonPath("$.decision").isEmpty()).andExpect(jsonPath("$.expired").value(false));
  }

  @Test
  void missingReservationReturns404OnlyForAnAuthorisedApprover() throws Exception {
    when(reservations.get(RESERVATION)).thenThrow(new RecordNotFoundException());
    mvc.perform(get(ROOT + "/" + RESERVATION).with(as("APPROVER"))).andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("/problems/not-found"));
    verifyNoInteractions(decisions);
  }

  @Test
  void approveUsesSessionActorAndServerRequesterDespiteSpoofedBodyFields() throws Exception {
    String body = "{\"decision\":\"APPROVED\",\"expectedVersion\":4,\"approverId\":\"" + REQUESTER
        + "\",\"actorId\":\"" + REQUESTER + "\",\"recipient\":\"attacker\"}";
    mvc.perform(decide(body)).andExpect(status().isOk())
        .andExpect(jsonPath("$.reservation.status").value("CONFIRMED"))
        .andExpect(jsonPath("$.reservation.version").value(5))
        .andExpect(jsonPath("$.decision.approverId").value(APPROVER.toString()))
        .andExpect(jsonPath("$.decision.decidedAt").value(DECIDED_AT.toString()));
    verify(decisions).record(RESERVATION, APPROVER, ApprovalDecisionType.APPROVED, null);
    assertThat(events.seen).singleElement().satisfies(event -> {
      assertThat(event.requesterId()).isEqualTo(REQUESTER);
      assertThat(event.approverId()).isEqualTo(APPROVER);
      assertThat(event.decidedAt()).isEqualTo(DECIDED_AT);
    });
  }

  @Test
  void expiredPendingReservationCanStillBeRejected() throws Exception {
    when(reservations.get(RESERVATION)).thenReturn(view("PENDING_APPROVAL", 4L, true, REQUESTER));
    mvc.perform(decide("{\"decision\":\"REJECTED\",\"comment\":\"Time has passed\",\"expectedVersion\":4}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.reservation.status").value("REJECTED"))
        .andExpect(jsonPath("$.decision.comment").value("Time has passed"));
    verify(reservations).reject(RESERVATION, 4L);
    verify(reservations, never()).confirm(any(), anyLong());
  }

  @Test
  void adminWithExplicitApproverRoleMayDecideAnotherUsersReservation() throws Exception {
    mvc.perform(post(ROOT + "/" + RESERVATION + "/decision").with(as("ADMIN", "APPROVER"))
        .with(realCsrf()).contentType("application/json").content(approve())).andExpect(status().isOk());
  }

  @Test
  void selfApprovalIs403AndNeverTransitionsOrRecords() throws Exception {
    when(reservations.get(RESERVATION)).thenReturn(view("PENDING_APPROVAL", 4L, false, APPROVER));
    mvc.perform(decide(approve())).andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("/problems/self-approval"));
    verify(reservations, never()).confirm(any(), anyLong());
    verify(reservations, never()).reject(any(), anyLong());
    verifyNoInteractions(decisions);
    assertThat(events.seen).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "{}", "{\"decision\":\"APPROVED\"}",
      "{\"decision\":\"APPROVE\",\"expectedVersion\":4}",
      "{\"decision\":\"APPROVED\",\"expectedVersion\":null}",
      "{\"decision\":\"APPROVED\",\"expectedVersion\":-1}",
      "{\"decision\":\"APPROVED\",\"expectedVersion\":4.9}",
      "{\"decision\":\"APPROVED\",\"expectedVersion\":\"4\"}",
      "{\"decision\":\"APPROVED\",\"expectedVersion\":9223372036854775808}",
      "{\"decision\":\"REJECTED\",\"expectedVersion\":4}",
      "{\"decision\":\"REJECTED\",\"comment\":\"  \",\"expectedVersion\":4}"})
  void invalidDecisionBodiesReturn400WithoutCallingThePorts(String body) throws Exception {
    mvc.perform(decide(body)).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/problems/validation")).andExpect(jsonPath("$.errors").isArray());
    verifyNoInteractions(reservations, decisions);
    assertThat(events.seen).isEmpty();
  }

  @Test
  void rejectionValidationPointsToComment() throws Exception {
    mvc.perform(decide("{\"decision\":\"REJECTED\",\"expectedVersion\":4}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("comment"));
  }

  @Test
  void commentLengthAndLongVersionBoundariesArePreserved() throws Exception {
    String tooLong = "x".repeat(1001);
    mvc.perform(decide("{\"decision\":\"APPROVED\",\"comment\":\"" + tooLong + "\",\"expectedVersion\":4}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("comment"));
    verifyNoInteractions(reservations, decisions);
    long version = 3_000_000_000L;
    mvc.perform(decide("{\"decision\":\"APPROVED\",\"comment\":\"" + "x".repeat(1000)
        + "\",\"expectedVersion\":" + version + "}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.reservation.version").value(version + 1));
    verify(reservations).confirm(RESERVATION, version);
  }

  @ParameterizedTest
  @EnumSource(ApprovalConflictException.Kind.class)
  void conflictsReturn409AndCurrentMetadataWithoutPublishing(ApprovalConflictException.Kind kind)
      throws Exception {
    var failure = new ApprovalConflictException(kind, "PENDING_APPROVAL", 8L);
    if (kind == ApprovalConflictException.Kind.DUPLICATE)
      when(decisions.record(any(), any(), any(), any())).thenThrow(failure);
    else when(reservations.confirm(any(), anyLong())).thenThrow(failure);
    mvc.perform(decide(approve())).andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("/problems/" + kind.type()))
        .andExpect(jsonPath("$.currentStatus").value("PENDING_APPROVAL"))
        .andExpect(jsonPath("$.currentVersion").value(8));
    assertThat(events.seen).isEmpty();
  }

  @Test
  void databaseFailureUsesSafeSharedProblemAndDoesNotPublish() throws Exception {
    when(decisions.record(any(), any(), any(), any()))
        .thenThrow(new DataAccessResourceFailureException("SELECT secret FROM private_table"));
    mvc.perform(decide(approve())).andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.type").value("/problems/unavailable"))
        .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private_table"))));
    assertThat(events.seen).isEmpty();
  }
}
