package sg.edu.nus.serms.reservation.domain;

import java.time.Instant;
import java.util.UUID;

/** What the approval module sees of a reservation (contract auth-and-approval.md §3). */
public record PendingReservation(
    UUID reservationId,
    UUID requesterId,
    String requesterName,
    EquipmentSummary equipment,
    Instant startAt,
    Instant endAt,
    String purpose,
    ReservationStatus status,
    long version,
    Instant createdAt,
    boolean expired) {

  public record EquipmentSummary(UUID equipmentId, String assetTag, String name, String category) {}
}
