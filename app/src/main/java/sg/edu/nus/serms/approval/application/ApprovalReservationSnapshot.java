package sg.edu.nus.serms.approval.application;

import java.util.Objects;
import java.util.UUID;

/** The reservation facts needed by the approval use case. */
public record ApprovalReservationSnapshot(UUID reservationId, UUID requesterId) {

  public ApprovalReservationSnapshot {
    Objects.requireNonNull(reservationId, "reservationId must not be null");
    Objects.requireNonNull(requesterId, "requesterId must not be null");
  }
}
