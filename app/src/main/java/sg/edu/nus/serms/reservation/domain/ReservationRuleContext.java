package sg.edu.nus.serms.reservation.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Facts a policy may look at. Gathered by the service inside the booking transaction.
 * {@code equipmentMaxDuration} is the item's own limit, or null when the global limit applies.
 */
public record ReservationRuleContext(
    ReservationRequest request,
    Instant now,
    int openReservations,
    boolean requiresApproval,
    boolean hasOverdueLoan,
    Duration equipmentMaxDuration) {}
