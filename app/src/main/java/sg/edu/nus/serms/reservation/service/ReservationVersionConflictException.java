package sg.edu.nus.serms.reservation.service;

import java.util.UUID;

/** The caller decided on an outdated version of the reservation (409). */
public class ReservationVersionConflictException extends RuntimeException {
  private final UUID reservationId;
  private final long currentVersion;

  public ReservationVersionConflictException(UUID reservationId, long currentVersion) {
    super("The reservation was changed by someone else. Refresh and try again.");
    this.reservationId = reservationId;
    this.currentVersion = currentVersion;
  }

  public UUID reservationId() {
    return reservationId;
  }

  public long currentVersion() {
    return currentVersion;
  }
}
