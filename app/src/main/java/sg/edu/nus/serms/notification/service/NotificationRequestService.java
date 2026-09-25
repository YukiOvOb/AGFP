package sg.edu.nus.serms.notification.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.serms.notification.domain.NotificationRequest;
import sg.edu.nus.serms.notification.repository.NotificationRequestStore;

@Service
public class NotificationRequestService {
  private final NotificationRequestStore store;

  public NotificationRequestService(NotificationRequestStore store) {
    this.store = store;
  }

  /** Joins the producer transaction so rollback removes the notification request too. */
  @Transactional
  public String requestOnce(NotificationRequest request) {
    return store.requestOnce(request);
  }
}
