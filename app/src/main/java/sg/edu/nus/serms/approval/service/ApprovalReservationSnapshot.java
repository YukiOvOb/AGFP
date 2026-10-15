package sg.edu.nus.serms.approval.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Read-only projection of the reservation module's public view, not a second reservation entity. */
public record ApprovalReservationSnapshot(
    UUID reservationId,
    UUID requesterId,
    String requesterName,
    EquipmentSummary equipment,
    Instant startAt,
    Instant endAt,
    String purpose,
    String status,
    long version,
    Instant createdAt,
    boolean expired) {

  public ApprovalReservationSnapshot {
    Objects.requireNonNull(reservationId, "reservationId must not be null");
    Objects.requireNonNull(requesterId, "requesterId must not be null");
    Objects.requireNonNull(requesterName, "requesterName must not be null");
    Objects.requireNonNull(equipment, "equipment must not be null");
    Objects.requireNonNull(startAt, "startAt must not be null");
    Objects.requireNonNull(endAt, "endAt must not be null");
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    if (version < 0) throw new IllegalArgumentException("version must not be negative");
  }

  public record EquipmentSummary(UUID equipmentId, String assetTag, String name, String category) {}
}
