package sg.edu.nus.serms.reservation.domain.policy;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import sg.edu.nus.serms.reservation.domain.ReservationRuleContext;
import sg.edu.nus.serms.reservation.domain.RuleViolation;

/**
 * Minimum and maximum borrowing duration (Proposal rule 10, Proposal 5.3). An item's own maximum
 * (V005 {@code max_borrow_duration}) replaces the global maximum for that item.
 */
public final class DurationPolicy implements ReservationPolicy {
  private final Duration minimum;
  private final Duration maximum;

  public DurationPolicy(Duration minimum, Duration maximum) {
    this.minimum = Objects.requireNonNull(minimum);
    this.maximum = Objects.requireNonNull(maximum);
    if (minimum.isNegative() || maximum.compareTo(minimum) < 0) {
      throw new IllegalArgumentException("Invalid duration limits");
    }
  }

  @Override
  public Optional<RuleViolation> evaluate(ReservationRuleContext c) {
    Duration requested = c.request().duration();
    if (requested.isNegative() || requested.isZero()) return Optional.empty();
    if (requested.compareTo(minimum) < 0) {
      return Optional.of(
          new RuleViolation("too-short", "A reservation must last at least " + human(minimum) + "."));
    }
    Duration itemLimit = c.equipmentMaxDuration();
    Duration limit = itemLimit == null ? maximum : itemLimit;
    if (requested.compareTo(limit) > 0) {
      return Optional.of(new RuleViolation("too-long", itemLimit == null
          ? "A reservation may last at most " + human(maximum) + "."
          : "This equipment may be reserved for at most " + human(itemLimit) + "."));
    }
    return Optional.empty();
  }

  static String human(Duration d) {
    if (d.toMinutes() % (24 * 60) == 0) {
      long days = d.toDays();
      return days + (days == 1 ? " day" : " days");
    }
    if (d.toMinutes() % 60 == 0) {
      long hours = d.toHours();
      return hours + (hours == 1 ? " hour" : " hours");
    }
    return d.toMinutes() + " minutes";
  }
}
