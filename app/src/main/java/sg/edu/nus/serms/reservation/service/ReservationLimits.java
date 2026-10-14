package sg.edu.nus.serms.reservation.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Configurable limits, {@code serms.reservation.*}. Defaults follow the team's rule table. */
@ConfigurationProperties("serms.reservation")
public record ReservationLimits(
    @DefaultValue("30m") Duration minDuration,
    @DefaultValue("7d") Duration maxDuration,
    @DefaultValue("90d") Duration bookingHorizon,
    @DefaultValue("3") int maxOpenReservations,
    @DefaultValue("true") boolean blockOverdueBorrowers) {}
