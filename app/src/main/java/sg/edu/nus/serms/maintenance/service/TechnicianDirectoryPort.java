package sg.edu.nus.serms.maintenance.service;

import java.util.UUID;

public interface TechnicianDirectoryPort {
  /** A future Identity adapter verifies that the user may act as a technician. */
  void ensureTechnician(UUID userId);
}
