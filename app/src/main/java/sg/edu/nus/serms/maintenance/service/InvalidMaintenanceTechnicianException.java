package sg.edu.nus.serms.maintenance.service;

import java.util.UUID;

public class InvalidMaintenanceTechnicianException extends IllegalArgumentException {
  private final UUID userId;

  public InvalidMaintenanceTechnicianException(UUID userId) {
    super("Maintenance assignment requires an ACTIVE MAINTAINER: " + userId);
    this.userId = userId;
  }

  public UUID getUserId() { return userId; }
}
