package sg.edu.nus.serms.notification.service;

import java.time.Clock;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import sg.edu.nus.serms.notification.domain.Notification;
import sg.edu.nus.serms.notification.repository.NotificationRepository;

@Service
public class NotificationInboxService {
  private final NotificationRepository repository;
  private final Clock clock;

  public NotificationInboxService(NotificationRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<Notification> inbox(String recipient, int page) {
    return repository.findByRecipientAndStatusOrderByDeliveredAtDesc(
        recipient,
        Notification.Status.DELIVERED,
        PageRequest.of(Math.max(0, Math.min(page, 10000)), 20));
  }

  @Transactional
  public void markRead(String id, String recipient) {
    var n =
        repository
            .lock(id)
            .filter(
                x ->
                    x.getRecipient().equals(recipient)
                        && x.getStatus() == Notification.Status.DELIVERED)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    n.markRead(clock.instant());
  }
}
