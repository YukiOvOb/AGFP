package sg.edu.nus.serms.approval.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import sg.edu.nus.serms.approval.domain.ApprovalCommand;
import sg.edu.nus.serms.approval.domain.ApprovalDecisionType;
import sg.edu.nus.serms.approval.domain.ApprovalFailure;
import sg.edu.nus.serms.approval.domain.ApprovalPolicyChain;
import sg.edu.nus.serms.approval.domain.DecisionCommentHandler;
import sg.edu.nus.serms.approval.domain.NoSelfApprovalHandler;
import sg.edu.nus.serms.approval.service.event.ReservationDecided;

class ApprovalServiceTest {

  private final ReservationApprovalPort reservations =
      org.mockito.Mockito.mock(ReservationApprovalPort.class);
  private final ApprovalDecisionPort decisions = org.mockito.Mockito.mock(ApprovalDecisionPort.class);
  private final ApplicationEventPublisher events =
      org.mockito.Mockito.mock(ApplicationEventPublisher.class);

  private final UUID reservationId = UUID.randomUUID();
  private final UUID requesterId = UUID.randomUUID();
  private final UUID approverId = UUID.randomUUID();
  private final UUID decisionId = UUID.randomUUID();
  private ApprovalService service;

  private static final Instant DECIDED_AT = Instant.parse("2026-10-06T12:00:00Z");

  private ApprovalReservationSnapshot snapshot(UUID requester, String status, long version) {
    return new ApprovalReservationSnapshot(reservationId, requester, "Requester",
        new ApprovalReservationSnapshot.EquipmentSummary(UUID.randomUUID(), "EQ-001", "Camera", "Media"),
        DECIDED_AT.plusSeconds(3600), DECIDED_AT.plusSeconds(7200), null, status, version,
        DECIDED_AT.minusSeconds(3600), false);
  }

  private RecordedApprovalDecision recorded(ApprovalDecisionType type, String comment) {
    return new RecordedApprovalDecision(decisionId, reservationId, approverId, type, comment, DECIDED_AT);
  }

  @BeforeEach
  void setUp() {
    service =
        new ApprovalService(
            reservations,
            decisions,
            new ApprovalPolicyChain(
                List.of(new NoSelfApprovalHandler(), new DecisionCommentHandler())),
            events);
  }

  @Test
  void approveLoadsReservationTransitionsPersistsAndPublishesInOrder() {
    ApprovalCommand command =
        new ApprovalCommand(reservationId, ApprovalDecisionType.APPROVED, null, 4L);
    ApprovalReservationSnapshot pending =
        snapshot(requesterId, "PENDING_APPROVAL", 4L);
    ApprovalReservationSnapshot confirmed =
        snapshot(requesterId, "CONFIRMED", 5L);
    when(reservations.get(reservationId)).thenReturn(pending);
    when(reservations.confirm(reservationId, 4L)).thenReturn(confirmed);
    when(decisions.record(reservationId, approverId, ApprovalDecisionType.APPROVED, null))
        .thenReturn(recorded(ApprovalDecisionType.APPROVED, null));

    ApprovalOutcome result = service.decide(approverId, command);

    assertThat(result).isEqualTo(new ApprovalOutcome(confirmed, recorded(ApprovalDecisionType.APPROVED, null)));
    InOrder order = inOrder(reservations, decisions, events);
    order.verify(reservations).get(reservationId);
    order.verify(reservations).confirm(reservationId, 4L);
    order.verify(decisions)
        .record(reservationId, approverId, ApprovalDecisionType.APPROVED, null);
    ArgumentCaptor<ReservationDecided> eventCaptor =
        ArgumentCaptor.forClass(ReservationDecided.class);
    order.verify(events).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().eventId()).isNotNull();
    assertThat(eventCaptor.getValue().approvalDecisionId()).isEqualTo(decisionId);
    assertThat(eventCaptor.getValue().reservationId()).isEqualTo(reservationId);
    assertThat(eventCaptor.getValue().requesterId()).isEqualTo(requesterId);
    assertThat(eventCaptor.getValue().approverId()).isEqualTo(approverId);
    assertThat(eventCaptor.getValue().decision()).isEqualTo(ApprovalDecisionType.APPROVED);
    assertThat(eventCaptor.getValue().comment()).isNull();
    assertThat(eventCaptor.getValue().decidedAt()).isEqualTo(DECIDED_AT);
  }

  @Test
  void rejectPassesReasonAndExpectedVersionToReservationService() {
    ApprovalCommand command =
        new ApprovalCommand(reservationId, ApprovalDecisionType.REJECTED, "Not available", 2L);
    ApprovalReservationSnapshot pending =
        snapshot(requesterId, "PENDING_APPROVAL", 2L);
    when(reservations.get(reservationId)).thenReturn(pending);
    when(reservations.reject(reservationId, 2L)).thenReturn(pending);
    when(decisions.record(
            reservationId, approverId, ApprovalDecisionType.REJECTED, "Not available"))
        .thenReturn(recorded(ApprovalDecisionType.REJECTED, "Not available"));

    service.decide(approverId, command);

    verify(reservations).reject(reservationId, 2L);
    verify(reservations, never()).confirm(reservationId, 2L);
    verify(decisions)
        .record(reservationId, approverId, ApprovalDecisionType.REJECTED, "Not available");
  }

  @Test
  void selfApprovalFailsBeforeTransitionOrPersistence() {
    ApprovalCommand command =
        new ApprovalCommand(reservationId, ApprovalDecisionType.APPROVED, null, 0L);
    when(reservations.get(reservationId))
        .thenReturn(snapshot(approverId, "PENDING_APPROVAL", 0L));

    assertThatThrownBy(() -> service.decide(approverId, command))
        .isInstanceOf(ApprovalPolicyViolationException.class)
        .extracting(error -> ((ApprovalPolicyViolationException) error).failure())
        .isEqualTo(ApprovalFailure.SELF_APPROVAL_NOT_ALLOWED);

    verify(reservations, never()).confirm(reservationId, 0L);
    verify(reservations, never()).reject(reservationId, 0L);
    verify(decisions, never()).record(reservationId, approverId, ApprovalDecisionType.APPROVED, null);
    verify(events, never()).publishEvent(org.mockito.ArgumentMatchers.any(Object.class));
  }

  @Test
  void blankRejectionReasonFailsBeforeTransitionOrPersistence() {
    ApprovalCommand command =
        new ApprovalCommand(reservationId, ApprovalDecisionType.REJECTED, "  ", 0L);
    when(reservations.get(reservationId))
        .thenReturn(snapshot(requesterId, "PENDING_APPROVAL", 0L));

    assertThatThrownBy(() -> service.decide(approverId, command))
        .isInstanceOf(ApprovalPolicyViolationException.class)
        .extracting(error -> ((ApprovalPolicyViolationException) error).failure())
        .isEqualTo(ApprovalFailure.REJECTION_REASON_REQUIRED);

    verify(reservations, never()).reject(reservationId, 0L);
    verify(decisions, never()).record(reservationId, approverId, ApprovalDecisionType.REJECTED, "  ");
    verify(events, never()).publishEvent(org.mockito.ArgumentMatchers.any(Object.class));
  }

  @Test
  void reservationReadFailureDoesNotContinueTheWorkflow() {
    ApprovalCommand command =
        new ApprovalCommand(reservationId, ApprovalDecisionType.APPROVED, null, 0L);
    RuntimeException failure = new RuntimeException("reservation not found");
    when(reservations.get(reservationId)).thenThrow(failure);

    assertThatThrownBy(() -> service.decide(approverId, command)).isSameAs(failure);

    verify(reservations, never()).confirm(reservationId, 0L);
    verify(reservations, never()).reject(reservationId, 0L);
    verify(decisions, never()).record(any(), any(), any(), any());
    verify(events, never()).publishEvent(any(Object.class));
  }

  @Test
  void reservationTransitionFailureDoesNotRecordOrPublishDecision() {
    ApprovalCommand command =
        new ApprovalCommand(reservationId, ApprovalDecisionType.APPROVED, null, 5L);
    RuntimeException failure = new RuntimeException("reservation version conflict");
    when(reservations.get(reservationId))
        .thenReturn(snapshot(requesterId, "PENDING_APPROVAL", 5L));
    when(reservations.confirm(reservationId, 5L)).thenThrow(failure);

    assertThatThrownBy(() -> service.decide(approverId, command)).isSameAs(failure);

    verify(decisions, never()).record(any(), any(), any(), any());
    verify(events, never()).publishEvent(any(Object.class));
  }

  @Test
  void decisionPersistenceFailureDoesNotPublishEvent() {
    ApprovalCommand command =
        new ApprovalCommand(reservationId, ApprovalDecisionType.REJECTED, "Not available", 1L);
    RuntimeException failure = new RuntimeException("decision persistence failed");
    when(reservations.get(reservationId))
        .thenReturn(snapshot(requesterId, "PENDING_APPROVAL", 1L));
    when(reservations.reject(reservationId, 1L))
        .thenReturn(snapshot(requesterId, "REJECTED", 2L));
    when(decisions.record(
            reservationId, approverId, ApprovalDecisionType.REJECTED, "Not available"))
        .thenThrow(failure);

    assertThatThrownBy(() -> service.decide(approverId, command)).isSameAs(failure);

    verify(events, never()).publishEvent(any(Object.class));
  }

  @Test
  void detailIncludesThePersistedFinalDecision() {
    var view = snapshot(requesterId, "CONFIRMED", 5L);
    var decision = recorded(ApprovalDecisionType.APPROVED, null);
    when(reservations.get(reservationId)).thenReturn(view);
    when(decisions.findByReservationId(reservationId)).thenReturn(Optional.of(decision));

    assertThat(service.get(reservationId)).isEqualTo(new ApprovalDetails(view, decision));
  }

  @Test
  void eventListenerFailureIsNotSwallowed() {
    var command = new ApprovalCommand(reservationId, ApprovalDecisionType.APPROVED, null, 4L);
    when(reservations.get(reservationId)).thenReturn(snapshot(requesterId, "PENDING_APPROVAL", 4L));
    when(reservations.confirm(reservationId, 4L)).thenReturn(snapshot(requesterId, "CONFIRMED", 5L));
    when(decisions.record(reservationId, approverId, ApprovalDecisionType.APPROVED, null))
        .thenReturn(recorded(ApprovalDecisionType.APPROVED, null));
    var failure = new RuntimeException("notification persistence failed");
    org.mockito.Mockito.doThrow(failure).when(events).publishEvent(any(Object.class));

    assertThatThrownBy(() -> service.decide(approverId, command)).isSameAs(failure);
  }
}
