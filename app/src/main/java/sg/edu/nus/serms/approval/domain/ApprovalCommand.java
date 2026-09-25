package sg.edu.nus.serms.approval.domain;

import java.util.Objects;
import java.util.UUID;

/** Input to the approval use case. The approver identity comes from authentication, not the client. */
public record ApprovalCommand(
    UUID reservationId,
    ApprovalDecisionType decision,
    String comment,
    long expectedVersion) {

  public ApprovalCommand {
    Objects.requireNonNull(reservationId, "reservationId must not be null");
    Objects.requireNonNull(decision, "decision must not be null");
    if (expectedVersion < 0) {
      throw new IllegalArgumentException("expectedVersion must not be negative");
    }
  }
}
