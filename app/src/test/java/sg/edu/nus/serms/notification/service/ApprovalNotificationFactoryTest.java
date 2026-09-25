package sg.edu.nus.serms.notification.service;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import sg.edu.nus.serms.approval.domain.ApprovalDecisionType;
import sg.edu.nus.serms.approval.service.event.ReservationDecided;
import sg.edu.nus.serms.notification.domain.NotificationType;

class ApprovalNotificationFactoryTest {
  private final ApprovalNotificationFactory factory = new ApprovalNotificationFactory();

  private ReservationDecided event(ApprovalDecisionType decision, String reason) {
    return new ReservationDecided(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        decision,
        reason);
  }

  @Test
  void approvalTargetsRequesterAndContainsConfirmation() {
    var e = event(ApprovalDecisionType.APPROVED, null);
    var r = factory.from(e);
    assertThat(r.recipient())
        .isEqualTo(e.requesterId().toString())
        .isNotEqualTo(e.approverId().toString());
    assertThat(r.type()).isEqualTo(NotificationType.BUSINESS_EVENT);
    assertThat(r.content()).contains(e.reservationId().toString(), "confirmed");
    assertThat(r.loanId()).isNull();
    assertThat(r.expectedDueAt()).isNull();
  }

  @Test
  void rejectionPreservesFullReasonWithinMessageLimit() {
    var r = factory.from(event(ApprovalDecisionType.REJECTED, "a".repeat(1000)));
    assertThat(r.content()).contains("rejected", "a".repeat(1000));
    assertThat(r.content().length()).isLessThanOrEqualTo(2000);
  }

  @Test
  void missingReasonAndOversizedCommentAreRejected() {
    for (String reason : new String[] {null, "", "  "})
      assertThatThrownBy(() -> factory.from(event(ApprovalDecisionType.REJECTED, reason)))
          .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> factory.from(event(ApprovalDecisionType.APPROVED, "x".repeat(1001))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void newEventIdAndDecisionIdDoNotChangeBusinessIdentity() {
    var e = event(ApprovalDecisionType.APPROVED, null);
    var replay =
        new ReservationDecided(
            UUID.randomUUID(),
            UUID.randomUUID(),
            e.reservationId(),
            e.requesterId(),
            e.approverId(),
            e.decision(),
            "replayed");
    assertThat(factory.from(replay).dedupKey()).isEqualTo(factory.from(e).dedupKey());
  }

  @Test
  void differentReservationsRemainDistinct() {
    var e = event(ApprovalDecisionType.APPROVED, null);
    var other =
        new ReservationDecided(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            e.requesterId(),
            e.approverId(),
            e.decision(),
            null);
    assertThat(factory.from(other).dedupKey()).isNotEqualTo(factory.from(e).dedupKey());
  }
}
