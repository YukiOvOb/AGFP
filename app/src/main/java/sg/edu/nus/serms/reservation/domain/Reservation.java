package sg.edu.nus.serms.reservation.domain;

import java.time.Instant;
import java.util.UUID;

/** A reservation as read back from the database, with the equipment facts shown to borrowers. */
public record Reservation(
    UUID reservationId,
    UUID requesterId,
    UUID equipmentId,
    String assetTag,
    String equipmentName,
    String location,
    Instant startAt,
    Instant endAt,
    ReservationStatus status,
    String purpose,
    Instant createdAt,
    long version,
    boolean checkedOut) {

  public boolean cancellable() {
    return status.isCancellable() && !checkedOut;
  }
}
