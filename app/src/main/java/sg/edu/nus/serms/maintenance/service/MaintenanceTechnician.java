package sg.edu.nus.serms.maintenance.service;

import java.util.Objects;
import java.util.UUID;

/** Eligible assignment directory projection, independent of Identity implementation. */
public record MaintenanceTechnician(UUID userId, String email, String displayName) {
  public MaintenanceTechnician {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(email, "email");
    Objects.requireNonNull(displayName, "displayName");
  }
}
