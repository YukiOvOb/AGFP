package sg.edu.nus.serms.reservation.domain;

import java.time.Instant;

/** Facts a policy may look at. Gathered by the service inside the booking transaction. */
public record ReservationRuleContext(
    ReservationRequest request, Instant now, int openReservations, boolean requiresApproval) {}
