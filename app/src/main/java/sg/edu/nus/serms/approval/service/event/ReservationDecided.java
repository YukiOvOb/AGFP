package sg.edu.nus.serms.approval.service.event;

import java.util.Objects;
import java.util.UUID;
import sg.edu.nus.serms.approval.domain.ApprovalDecisionType;

/** Public approval event consumed by audit and notification modules. */
public record ReservationDecided(
    UUID eventId,
    UUID approvalDecisionId,
    UUID reservationId,
    UUID requesterId,
    UUID approverId,
    ApprovalDecisionType decision,
    String comment) {

  public ReservationDecided {
    Objects.requireNonNull(eventId, "eventId must not be null");
    Objects.requireNonNull(approvalDecisionId, "approvalDecisionId must not be null");
    Objects.requireNonNull(reservationId, "reservationId must not be null");
    Objects.requireNonNull(requesterId, "requesterId must not be null");
    Objects.requireNonNull(approverId, "approverId must not be null");
    Objects.requireNonNull(decision, "decision must not be null");
  }
}
