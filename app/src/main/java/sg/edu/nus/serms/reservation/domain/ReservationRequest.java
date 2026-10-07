package sg.edu.nus.serms.reservation.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** What a borrower asks for. Structural checks only; business limits live in the policies. */
public record ReservationRequest(UUID equipmentId, Instant startAt, Instant endAt, String purpose) {
  public static final int MAX_PURPOSE_LENGTH = 500;

  public ReservationRequest {
    Objects.requireNonNull(equipmentId, "equipmentId");
    Objects.requireNonNull(startAt, "startAt");
    Objects.requireNonNull(endAt, "endAt");
    purpose = purpose == null || purpose.isBlank() ? null : purpose.strip();
    if (purpose != null && purpose.length() > MAX_PURPOSE_LENGTH) {
      throw new IllegalArgumentException("purpose is too long");
    }
  }

  public Duration duration() {
    return Duration.between(startAt, endAt);
  }
}
