package sg.edu.nus.serms.reservation.service.event;

import java.util.UUID;

/** Published inside the cancelling transaction; the slot is free once it commits. */
public record ReservationCancelled(
    UUID eventId, UUID reservationId, UUID requesterId, UUID equipmentId) {}
