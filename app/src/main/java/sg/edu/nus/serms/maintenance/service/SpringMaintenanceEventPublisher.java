package sg.edu.nus.serms.maintenance.service;

import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import sg.edu.nus.serms.maintenance.domain.MaintenanceAssigned;
import sg.edu.nus.serms.maintenance.domain.MaintenanceCompleted;

/** Publishes UC04 events through Spring without prescribing subscriber behaviour. */
@Component
public final class SpringMaintenanceEventPublisher implements MaintenanceEventPublisher {
  private final ApplicationEventPublisher publisher;

  public SpringMaintenanceEventPublisher(ApplicationEventPublisher publisher) {
    this.publisher = Objects.requireNonNull(publisher, "publisher");
  }

  @Override
  public void publish(MaintenanceAssigned event) {
    publisher.publishEvent(Objects.requireNonNull(event, "event"));
  }

  @Override
  public void publish(MaintenanceCompleted event) {
    publisher.publishEvent(Objects.requireNonNull(event, "event"));
  }
}
