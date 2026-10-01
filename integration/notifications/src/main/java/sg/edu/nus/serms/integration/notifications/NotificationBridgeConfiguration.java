package sg.edu.nus.serms.integration.notifications;

import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import sg.edu.nus.serms.notification.domain.ReminderPolicy;

@Configuration
@Profile("serms-postgres")
public class NotificationBridgeConfiguration {
    @Bean @Primary
    public PostgresLoanReminderGuard postgresLoanReminderGuard(JdbcTemplate jdbc,
            @Value("${serms.notifications.due-soon-lead:PT24H}") Duration leadTime) {
        return new PostgresLoanReminderGuard(jdbc, new ReminderPolicy(leadTime));
    }

    @Bean @Primary
    public PostgresNotificationRequestStore postgresNotificationRequestStore(JdbcTemplate jdbc, Clock clock) {
        return new PostgresNotificationRequestStore(jdbc, clock);
    }

    @Bean
    public LoanNotificationRequestFactory loanNotificationRequestFactory(JdbcTemplate jdbc) {
        return new LoanNotificationRequestFactory(jdbc);
    }
}