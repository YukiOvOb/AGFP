package sg.edu.nus.serms.approval.service;

import java.util.Objects;

/** A successful transition and its persisted decision. */
public record ApprovalOutcome(
    ApprovalReservationSnapshot reservation, RecordedApprovalDecision decision) {

  public ApprovalOutcome {
    Objects.requireNonNull(reservation, "reservation must not be null");
    Objects.requireNonNull(decision, "decision must not be null");
  }
}
