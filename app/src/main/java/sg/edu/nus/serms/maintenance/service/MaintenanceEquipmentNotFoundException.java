package sg.edu.nus.serms.maintenance.service;

import java.util.UUID;

public class MaintenanceEquipmentNotFoundException extends RuntimeException {
  public MaintenanceEquipmentNotFoundException(UUID id) {
    super("Equipment does not exist: " + id);
  }
}
