package sg.edu.nus.serms.notification.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.serms.approval.service.event.ReservationDecided;

/** Synchronous request persistence only; the existing worker handles delivery after commit. */
@Component
public class ApprovalNotificationListener {
  private final ApprovalNotificationFactory factory;
  private final NotificationRequestService requests;

  public ApprovalNotificationListener(
      ApprovalNotificationFactory factory, NotificationRequestService requests) {
    this.factory = factory;
    this.requests = requests;
  }

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void on(ReservationDecided event) {
    requests.requestOnce(factory.from(event));
  }
}
