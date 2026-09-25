package edu.nus.serms.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Reservation(UUID id, UUID userId, UUID equipmentId,
                          Instant startsAt, Instant endsAt, Status status) {
    public Reservation {
        Objects.requireNonNull(id);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(equipmentId);
        Objects.requireNonNull(status);
        validateWindow(startsAt, endsAt);
    }
    public static void validateWindow(Instant start, Instant end) {
        Objects.requireNonNull(start);
        Objects.requireNonNull(end);
        if (!start.isBefore(end)) throw new IllegalArgumentException("Start must precede end");
        if (start.getNano() % 1000 != 0 || end.getNano() % 1000 != 0)
            throw new IllegalArgumentException("Use microsecond precision or coarser");
    }
    public enum Status { PENDING, CONFIRMED, REJECTED, CANCELLED, FULFILLED }
}