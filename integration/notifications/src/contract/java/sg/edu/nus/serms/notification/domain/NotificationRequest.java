package sg.edu.nus.serms.notification.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/** Identity must come from a persisted business event or a stable reminder period. */
public record NotificationRequest(
    String recipient,
    NotificationType type,
    String sourceId,
    String period,
    Long loanId,
    Instant expectedDueAt,
    String content) {
  public NotificationRequest {
    require(recipient, 100);
    require(sourceId, 100);
    require(period, 100);
    require(content, 2000);
    Objects.requireNonNull(type, "type");
    if (type != NotificationType.BUSINESS_EVENT
        && (loanId == null || loanId <= 0 || expectedDueAt == null))
      throw new IllegalArgumentException("Reminders require a loan and expected due time");
  }

  private static void require(String s, int max) {
    if (s == null || s.isBlank() || s.length() > max)
      throw new IllegalArgumentException("Missing or oversized notification field");
  }

  public String dedupKey() {
    // Length prefixes prevent ambiguous concatenation; include due date so a rescheduled loan gets
    // a new reminder.
    StringBuilder value = new StringBuilder();
    for (String part :
        new String[] {
          recipient,
          type.name(),
          sourceId,
          period,
          String.valueOf(loanId),
          String.valueOf(expectedDueAt)
        }) value.append(part.length()).append(':').append(part);
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(value.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
