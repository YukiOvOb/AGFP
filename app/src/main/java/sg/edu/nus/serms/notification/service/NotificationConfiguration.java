package sg.edu.nus.serms.notification.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;

@Configuration
public class NotificationConfiguration {
  @Bean
  @ConditionalOnMissingBean(LoanReminderGuard.class)
  LoanReminderGuard unavailableLoanAdapter() {
    // Fail closed until UC-03 is integrated; never pretend a missing loan adapter is a valid loan.
    return (id, recipient, due, type, now) -> LoanReminderGuard.Result.UNAVAILABLE;
  }
}
