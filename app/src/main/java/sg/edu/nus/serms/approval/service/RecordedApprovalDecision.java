package sg.edu.nus.serms.approval.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import sg.edu.nus.serms.approval.domain.ApprovalDecisionType;

/** Immutable persistence result; decidedAt must be the timestamp stored with this decision. */
public record RecordedApprovalDecision(
    UUID approvalDecisionId,
    UUID reservationId,
    UUID approverId,
    ApprovalDecisionType decision,
    String comment,
    Instant decidedAt) {

  public RecordedApprovalDecision {
    Objects.requireNonNull(approvalDecisionId, "approvalDecisionId must not be null");
    Objects.requireNonNull(reservationId, "reservationId must not be null");
    Objects.requireNonNull(approverId, "approverId must not be null");
    Objects.requireNonNull(decision, "decision must not be null");
    Objects.requireNonNull(decidedAt, "decidedAt must not be null");
  }
}
