package sg.edu.nus.serms.reservation.domain.policy;

import java.util.Optional;
import sg.edu.nus.serms.reservation.domain.ReservationRuleContext;
import sg.edu.nus.serms.reservation.domain.RuleViolation;

/** A borrower may hold only a limited number of open reservations at once (Proposal rule 11). */
public final class OpenReservationQuotaPolicy implements ReservationPolicy {
  private final int maximum;

  public OpenReservationQuotaPolicy(int maximum) {
    if (maximum < 1) throw new IllegalArgumentException("The quota must be at least 1");
    this.maximum = maximum;
  }

  @Override
  public Optional<RuleViolation> evaluate(ReservationRuleContext c) {
    if (c.openReservations() >= maximum) {
      return Optional.of(
          new RuleViolation(
              "quota-exceeded",
              "You already have "
                  + c.openReservations()
                  + " open reservations; the limit is "
                  + maximum
                  + ". Cancel one or wait until one ends."));
    }
    return Optional.empty();
  }
}
