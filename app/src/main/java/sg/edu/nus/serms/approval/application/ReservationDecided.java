package sg.edu.nus.serms.approval.application;

import java.util.Objects;
import java.util.UUID;
import sg.edu.nus.serms.approval.domain.ApprovalDecisionType;

/** Published only after the reservation transition and decision record succeed. */
public record ReservationDecided(
    UUID approvalDecisionId,
    UUID reservationId,
    UUID approverId,
    ApprovalDecisionType decision,
    String comment) {

  public ReservationDecided {
    Objects.requireNonNull(approvalDecisionId, "approvalDecisionId must not be null");
    Objects.requireNonNull(reservationId, "reservationId must not be null");
    Objects.requireNonNull(approverId, "approverId must not be null");
    Objects.requireNonNull(decision, "decision must not be null");
  }
}
