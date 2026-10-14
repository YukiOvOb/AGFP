package sg.edu.nus.serms.notification.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.serms.loan.service.LoanEvent;
import sg.edu.nus.serms.notification.domain.NotificationRequest;
import sg.edu.nus.serms.notification.domain.NotificationType;

/** Saves borrower updates in the same transaction as physical checkout or return. */
@Component
public class LoanNotificationListener {
  private final NotificationRequestService requests;
  private final NotificationText text;

  public LoanNotificationListener(NotificationRequestService requests, NotificationText text) {
    this.requests = requests;
    this.text = text;
  }

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void on(LoanEvent event) {
    String equipmentName = text.equipment(event.equipmentId());
    String content =
        switch (event.action()) {
          case "CHECKED_OUT" ->
              "Your loan of " + equipmentName + " is due at " + text.time(event.dueAt()) + ".";
          case "RETURNED" ->
              "Your loan of "
                  + equipmentName
                  + (event.late() ? " was returned late" : " was returned")
                  + ("DAMAGED".equals(event.returnCondition()) ? " with reported damage." : ".");
          default -> throw new IllegalArgumentException("Unknown loan event: " + event.action());
        };
    requests.requestOnce(
        new NotificationRequest(
            event.borrowerId().toString(),
            NotificationType.BUSINESS_EVENT,
            "loan:" + event.loanId() + ":" + event.action(),
            "once",
            null,
            null,
            content));
  }
}
