package sg.edu.nus.serms.approval.service;

import java.util.Objects;
import java.util.UUID;

/** Exact final-decision uniqueness violation. Let the business transaction roll back before reads. */
public final class ApprovalDecisionAlreadyRecordedException extends RuntimeException {
  private final UUID reservationId;

  public ApprovalDecisionAlreadyRecordedException(UUID reservationId, Throwable cause) {
    super("A final approval decision already exists.", cause);
    this.reservationId = Objects.requireNonNull(reservationId);
  }

  public UUID reservationId() { return reservationId; }
}
