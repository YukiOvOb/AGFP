package sg.edu.nus.serms.approval.application;

import java.util.UUID;
import sg.edu.nus.serms.approval.domain.ApprovalDecisionType;

/** Persistence boundary for the immutable decision record. */
public interface ApprovalDecisionPort {

  UUID record(
      UUID reservationId, UUID approverId, ApprovalDecisionType decision, String comment);
}
