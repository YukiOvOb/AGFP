package sg.edu.nus.serms.loan.domain;

import java.time.Instant;
import java.util.UUID;

public record CheckoutCandidate(UUID reservationId, UUID equipmentId, String equipmentName,
    UUID borrowerId, String borrowerName, Instant startAt, Instant endAt, long version) {}
