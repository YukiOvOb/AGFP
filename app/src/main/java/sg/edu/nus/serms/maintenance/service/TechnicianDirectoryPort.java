package sg.edu.nus.serms.maintenance.service;

import java.util.UUID;
import java.util.List;

public interface TechnicianDirectoryPort {
  /** A future Identity adapter verifies an existing ACTIVE user with the MAINTAINER role. */
  void ensureTechnician(UUID userId);

  /** ACTIVE MAINTAINER users, ordered by display name and user ID. */
  List<MaintenanceTechnician> findActiveTechnicians();
}
