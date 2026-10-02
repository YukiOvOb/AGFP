package edu.nus.serms.domain;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Reservation(UUID reservationId, UUID requesterId, UUID equipmentId,
                          Instant startAt, Instant endAt, Status status, String purpose, int version) {
    public Reservation {
        Objects.requireNonNull(reservationId);
        Objects.requireNonNull(requesterId);
        Objects.requireNonNull(equipmentId);
        Objects.requireNonNull(status);
        validateWindow(startAt, endAt);
        if (version < 0) throw new IllegalArgumentException("Negative version");
    }
    public static void validateWindow(Instant start, Instant end) {
        Objects.requireNonNull(start);
        Objects.requireNonNull(end);
        if (!start.isBefore(end)) throw new IllegalArgumentException("Start must precede end");
        if (start.getNano() % 1000 != 0 || end.getNano() % 1000 != 0)
            throw new IllegalArgumentException("Use microsecond precision or coarser");
    }
    public enum Status { PENDING_APPROVAL, CONFIRMED, REJECTED, CANCELLED, FULFILLED }
}
