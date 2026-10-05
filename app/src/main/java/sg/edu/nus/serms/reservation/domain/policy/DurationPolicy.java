package sg.edu.nus.serms.reservation.domain.policy;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import sg.edu.nus.serms.reservation.domain.ReservationRuleContext;
import sg.edu.nus.serms.reservation.domain.RuleViolation;

/** Minimum and maximum borrowing duration (Proposal rule 10). */
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
    if (requested.compareTo(maximum) > 0) {
      return Optional.of(
          new RuleViolation("too-long", "A reservation may last at most " + human(maximum) + "."));
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
