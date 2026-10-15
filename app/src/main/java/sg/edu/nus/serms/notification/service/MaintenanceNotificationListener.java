package sg.edu.nus.serms.notification.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.serms.maintenance.domain.MaintenanceAssigned;
import sg.edu.nus.serms.maintenance.domain.MaintenanceCompleted;
import sg.edu.nus.serms.maintenance.repository.MaintenanceCaseRepository;
import sg.edu.nus.serms.notification.domain.NotificationRequest;
import sg.edu.nus.serms.notification.domain.NotificationType;

/** Maintenance requests join the business transaction; completion targets the case reporter. */
@Component
public class MaintenanceNotificationListener {
  private final NotificationRequestService requests;
  private final MaintenanceCaseRepository cases;
  private final NotificationText text;

  public MaintenanceNotificationListener(NotificationRequestService requests,
      MaintenanceCaseRepository cases, NotificationText text) {
    this.requests = requests;
    this.cases = cases;
    this.text = text;
  }

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void on(MaintenanceAssigned event) {
    requests.requestOnce(
        new NotificationRequest(
            event.technicianId().toString(),
            NotificationType.BUSINESS_EVENT,
            "maintenance-assigned:" + event.maintenanceCaseId(),
            "once",
            null,
            null,
            "You were assigned maintenance for " + text.equipment(event.equipmentId()) + "."));
  }

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void on(MaintenanceCompleted event) {
    var item = cases.findById(event.maintenanceCaseId())
        .orElseThrow(() -> new IllegalStateException("Completed maintenance case not found"));
    if (!item.equipmentId().equals(event.equipmentId())
        || !event.technicianId().equals(item.assignedTo())
        || item.status() != event.outcome()) {
      throw new IllegalStateException("Completed maintenance event does not match the saved case");
    }
    String result = switch (event.outcome()) {
      case RESOLVED -> "resolved";
      case UNREPAIRABLE -> "marked unrepairable";
      default -> throw new IllegalStateException("Unexpected maintenance completion outcome");
    };
    requests.requestOnce(new NotificationRequest(
        item.reportedBy().toString(),
        NotificationType.BUSINESS_EVENT,
        "maintenance-completed:" + event.maintenanceCaseId(),
        "once",
        null,
        null,
        "Maintenance for " + text.equipment(event.equipmentId()) + " was " + result + "."));
  }
}
