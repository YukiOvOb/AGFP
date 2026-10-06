package sg.edu.nus.serms.notification.domain;

import java.time.*;
import java.util.Optional;

/** Pure clock-boundary rule shared by the future UC-03 loan scanner and guard. */
public record ReminderPolicy(Duration leadTime) {
  public ReminderPolicy {
    if (leadTime == null || leadTime.isNegative())
      throw new IllegalArgumentException("Invalid lead time");
  }

  public Optional<NotificationType> classify(boolean active, Instant dueAt, Instant now) {
    if (!active) return Optional.empty();
    if (now.isAfter(dueAt)) return Optional.of(NotificationType.OVERDUE);
    if (!now.isBefore(dueAt.minus(leadTime))) return Optional.of(NotificationType.DUE_SOON);
    return Optional.empty();
  }
}
