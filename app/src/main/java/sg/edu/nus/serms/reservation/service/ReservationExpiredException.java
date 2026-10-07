package sg.edu.nus.serms.reservation.service;

import java.util.UUID;
import sg.edu.nus.serms.reservation.domain.ReservationStatus;

/**
 * A pending reservation whose start time has passed cannot be approved, only rejected (409).
 * Carries the current status and version so the approver's page can refresh.
 */
public class ReservationExpiredException extends RuntimeException {
  private final UUID reservationId;
  private final ReservationStatus currentStatus;
  private final long currentVersion;

  public ReservationExpiredException(
      UUID reservationId, ReservationStatus currentStatus, long currentVersion) {
    super("This request has expired: its start time has passed. It can only be rejected.");
    this.reservationId = reservationId;
    this.currentStatus = currentStatus;
    this.currentVersion = currentVersion;
  }

  public UUID reservationId() {
    return reservationId;
  }

  public ReservationStatus currentStatus() {
    return currentStatus;
  }

  public long currentVersion() {
    return currentVersion;
  }
}
