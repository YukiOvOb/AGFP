package sg.edu.nus.serms.maintenance.service;

import java.util.UUID;

public interface TechnicianDirectoryPort {
  /** A future Identity adapter verifies an existing ACTIVE user with the MAINTAINER role. */
  void ensureTechnician(UUID userId);
}
