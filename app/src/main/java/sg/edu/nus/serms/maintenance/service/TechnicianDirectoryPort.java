package sg.edu.nus.serms.maintenance.service;

import java.util.UUID;
import java.util.List;

public interface TechnicianDirectoryPort {
  /**
   * Throws {@link InvalidMaintenanceTechnicianException} unless the user exists, is ACTIVE and
   * holds the MAINTAINER role.
   */
  void ensureTechnician(UUID userId);

  /** ACTIVE MAINTAINER users, ordered by display name and user ID. */
  List<MaintenanceTechnician> findActiveTechnicians();
}
