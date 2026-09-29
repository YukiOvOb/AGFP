package sg.edu.nus.serms.maintenance.service;

import sg.edu.nus.serms.maintenance.domain.MaintenanceAssigned;
import sg.edu.nus.serms.maintenance.domain.MaintenanceCompleted;

public interface MaintenanceEventPublisher {
  void publish(MaintenanceAssigned event);

  void publish(MaintenanceCompleted event);
}
