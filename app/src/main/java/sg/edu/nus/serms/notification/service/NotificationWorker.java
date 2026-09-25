package sg.edu.nus.serms.notification.service;

import java.time.Clock;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import sg.edu.nus.serms.notification.repository.NotificationRepository;

@Component
@ConditionalOnProperty(name = "serms.notifications.worker-enabled", havingValue = "true")
public class NotificationWorker {
  private final NotificationRepository repository;
  private final NotificationDeliveryService delivery;
  private final Clock clock;

  public NotificationWorker(
      NotificationRepository repository, NotificationDeliveryService delivery, Clock clock) {
    this.repository = repository;
    this.delivery = delivery;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${serms.notifications.poll-ms:30000}")
  public void deliverPending() {
    for (String id : repository.pending(clock.instant(), PageRequest.of(0, 100))) {
      try {
        delivery.deliver(id);
      } catch (RuntimeException e) {
        LoggerFactory.getLogger(getClass())
            .warn(
                "Notification transaction rolled back; next poll will recover request {} ({})",
                id,
                e.getClass().getSimpleName());
      }
    }
  }
}
