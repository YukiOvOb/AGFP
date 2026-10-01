package sg.edu.nus.serms.notification.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
public class NotificationEventListener {
  private final NotificationRequestService service;

  public NotificationEventListener(NotificationRequestService service) {
    this.service = service;
  }

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void on(NotificationRequested event) {
    service.requestOnce(event.request());
  }
}
