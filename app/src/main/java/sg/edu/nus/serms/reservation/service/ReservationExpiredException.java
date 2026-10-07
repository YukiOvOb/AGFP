package sg.edu.nus.serms.reservation.service;

import java.util.UUID;

/** A pending reservation whose start time has passed cannot be approved, only rejected (409). */
public class ReservationExpiredException extends RuntimeException {
  private final UUID reservationId;

  public ReservationExpiredException(UUID reservationId) {
    super("This request has expired: its start time has passed. It can only be rejected.");
    this.reservationId = reservationId;
  }

  public UUID reservationId() {
    return reservationId;
  }
}
