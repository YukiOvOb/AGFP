package sg.edu.nus.serms.reservation.service.event;

import java.time.Instant;
import java.util.UUID;
import sg.edu.nus.serms.reservation.domain.ReservationStatus;

/**
 * Published inside the booking transaction. {@code status} is PENDING_APPROVAL when the equipment
 * needs approval (approval and notification modules listen), CONFIRMED otherwise.
 */
public record ReservationSubmitted(
    UUID eventId,
    UUID reservationId,
    UUID requesterId,
    UUID equipmentId,
    Instant startAt,
    Instant endAt,
    ReservationStatus status) {}
