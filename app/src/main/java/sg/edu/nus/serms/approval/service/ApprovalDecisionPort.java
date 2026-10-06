package sg.edu.nus.serms.approval.service;

import java.util.Optional;
import java.util.UUID;
import sg.edu.nus.serms.approval.domain.ApprovalDecisionType;

/** Persistence boundary for the immutable decision record. */
public interface ApprovalDecisionPort {

  /** Joins the caller's transaction and returns the actual persisted timestamp, not a new clock reading. */
  RecordedApprovalDecision record(
      UUID reservationId, UUID approverId, ApprovalDecisionType decision, String comment);

  Optional<RecordedApprovalDecision> findByReservationId(UUID reservationId);
}
