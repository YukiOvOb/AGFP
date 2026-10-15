package sg.edu.nus.serms.identity.service;

import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.serms.identity.repository.AdminUserRepository;

/** Public Identity boundary used by the audit reader, never an implicit role grant. */
@Service
public class AdminAccess {
  private final AdminUserRepository users;
  public AdminAccess(AdminUserRepository users) { this.users = users; }

  @Transactional(readOnly = true)
  @PreAuthorize("hasRole('ADMIN')")
  public void requireAdministrator(UUID actorId) {
    if (actorId == null || !users.isActiveAdministrator(actorId))
      throw new AccessDeniedException("An active administrator is required");
  }
}
