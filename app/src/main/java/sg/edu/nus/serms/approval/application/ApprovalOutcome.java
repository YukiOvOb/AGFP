package sg.edu.nus.serms.approval.application;

import java.util.Objects;
import java.util.UUID;

/** Result of a successful decision, ready for the future REST response adapter. */
public record ApprovalOutcome(
    ApprovalReservationSnapshot reservation, UUID approvalDecisionId) {

  public ApprovalOutcome {
    Objects.requireNonNull(reservation, "reservation must not be null");
    Objects.requireNonNull(approvalDecisionId, "approvalDecisionId must not be null");
  }
}
