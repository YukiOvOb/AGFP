package sg.edu.nus.serms.notification.service;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.serms.notification.domain.*;
import sg.edu.nus.serms.notification.repository.NotificationDeliveryStore;
import sg.edu.nus.serms.notification.repository.NotificationRepository;

@Service
public class NotificationDeliveryService {
  private final NotificationRepository repository;
  private final LoanReminderGuard guard;
  private final Clock clock;
  private final NotificationDeliveryStore store;
  private final int maxAttempts;
  private final long retrySeconds;

  public NotificationDeliveryService(
      NotificationRepository repository,
      LoanReminderGuard guard,
      Clock clock,
      NotificationDeliveryStore store,
      @Value("${serms.notifications.max-attempts:3}") int maxAttempts,
      @Value("${serms.notifications.retry-seconds:60}") long retrySeconds) {
    if (maxAttempts < 1 || retrySeconds < 1)
      throw new IllegalArgumentException("Retry policy must be positive");
    this.repository = repository;
    this.guard = guard;
    this.clock = clock;
    this.store = store;
    this.maxAttempts = maxAttempts;
    this.retrySeconds = retrySeconds;
  }

  @Transactional
  public void deliver(String id) {
    var now = clock.instant();
    var snapshot = repository.findById(id).orElse(null);
    if (snapshot == null || !snapshot.eligible(now)) return;
    var result = LoanReminderGuard.Result.VALID;
    // Acquire shared business locks before notification lock; adapter must retain them to commit.
    if (snapshot.getType() != NotificationType.BUSINESS_EVENT)
      result =
          guard.check(
              snapshot.getLoanId(),
              snapshot.getRecipient(),
              snapshot.getExpectedDueAt(),
              snapshot.getType(),
              now);
    var n = repository.lock(id).orElseThrow();
    // Refresh after lock: another worker may have completed while this worker awaited business
    // locks.
    store.refreshLocked(n);
    if (!n.eligible(now)) return;
    if (result == LoanReminderGuard.Result.UNAVAILABLE) {
      n.fail(now, maxAttempts, retrySeconds, "LOAN_CHECK_UNAVAILABLE");
    } else if (result != LoanReminderGuard.Result.VALID) {
      n.cancel(result.name());
    } else {
      n.deliver(now);
    } // In-app delivery is a transactional visibility change, no external side effect.
    repository.flush();
    store.recordAttempt(n, now);
  }
}
