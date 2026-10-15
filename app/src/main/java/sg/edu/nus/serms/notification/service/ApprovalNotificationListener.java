package sg.edu.nus.serms.notification.service;

import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.serms.approval.service.event.ReservationDecided;

/** Synchronous request persistence only; the existing worker handles delivery after commit. */
@Component
public class ApprovalNotificationListener {
  private final ApprovalNotificationFactory factory;
  private final NotificationRequestService requests;
  private final NotificationText text;
  private final Environment environment;

  public ApprovalNotificationListener(
      ApprovalNotificationFactory factory, NotificationRequestService requests, NotificationText text, Environment environment) {
    this.factory = factory;
    this.requests = requests;
    this.text = text;
    this.environment = environment;
  }

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void on(ReservationDecided event) {
    String equipment = environment.acceptsProfiles(Profiles.of("serms-postgres"))
        ? text.reservationEquipment(event.reservationId()) : "reservation " + event.reservationId();
    requests.requestOnce(factory.from(event, equipment));
  }
}
