package sg.edu.nus.serms.reservation.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sg.edu.nus.serms.reservation.domain.policy.BookingHorizonPolicy;
import sg.edu.nus.serms.reservation.domain.policy.DurationPolicy;
import sg.edu.nus.serms.reservation.domain.policy.OpenReservationQuotaPolicy;
import sg.edu.nus.serms.reservation.domain.policy.OverdueLoanPolicy;
import sg.edu.nus.serms.reservation.domain.policy.ReservationPolicy;
import sg.edu.nus.serms.reservation.domain.policy.ReservationRules;
import sg.edu.nus.serms.reservation.domain.policy.StartInFuturePolicy;
import sg.edu.nus.serms.reservation.repository.ReservationRepository;

/** Wires the UC01 rules (Strategy pattern) and services. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ReservationLimits.class)
public class ReservationConfiguration {

  @Bean
  ReservationRules reservationRules(ReservationLimits limits) {
    return rulesFor(limits);
  }

  /** The standard rule set for the given limits; order is the order violations are reported. */
  public static ReservationRules rulesFor(ReservationLimits limits) {
    List<ReservationPolicy> policies =
        new ArrayList<>(
            List.of(
                new StartInFuturePolicy(),
                new DurationPolicy(limits.minDuration(), limits.maxDuration()),
                new BookingHorizonPolicy(limits.bookingHorizon()),
                new OpenReservationQuotaPolicy(limits.maxOpenReservations())));
    if (limits.blockOverdueBorrowers()) policies.add(new OverdueLoanPolicy());
    return new ReservationRules(policies);
  }

  @Bean
  ReservationService reservationService(
      ReservationRepository repository,
      ReservationRules rules,
      ApplicationEventPublisher events,
      Clock clock) {
    return new ReservationService(repository, rules, events, clock);
  }

  @Bean
  ReservationApprovalService reservationApprovalService(ReservationRepository repository, Clock clock) {
    return new DefaultReservationApprovalService(repository, clock);
  }

  @Bean
  ReservationApprovalAdapter reservationApprovalAdapter(ReservationApprovalService reservations) {
    return new ReservationApprovalAdapter(reservations);
  }
}
