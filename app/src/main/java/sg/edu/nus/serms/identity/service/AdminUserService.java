package sg.edu.nus.serms.identity.service;

import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.serms.identity.domain.AdminUser;
import sg.edu.nus.serms.identity.domain.AdminUserRules;
import sg.edu.nus.serms.identity.repository.AdminUserRepository;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserService {
  private final AdminUserRepository users;
  private final AdminAccess access;
  private final PasswordEncoder passwords;

  public AdminUserService(AdminUserRepository users, AdminAccess access, PasswordEncoder passwords) {
    this.users = users; this.access = access; this.passwords = passwords;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public AdminUser.Page list(UUID actor, String search, int page, int size) {
    access.requireAdministrator(actor);
    AdminUserRules.page(page, size);
    return users.list(AdminUserRules.search(search), page, size);
  }

  @Transactional
  public AdminUser create(UUID actor, String email, String displayName, String password, Set<AdminUser.Role> roles) {
    access.requireAdministrator(actor);
    String normalizedEmail = AdminUserRules.email(email);
    String normalizedName = AdminUserRules.displayName(displayName);
    AdminUserRules.password(password);
    AdminUserRules.roles(roles);
    var assignedRoles = Set.copyOf(roles);
    UUID id = UUID.randomUUID();
    users.create(id, normalizedEmail, normalizedName, passwords.encode(password), assignedRoles);
    users.auditCreation(actor, id);
    return new AdminUser(id, normalizedEmail, normalizedName, "ACTIVE", assignedRoles);
  }

  @Transactional
  public AdminUser updateDisplayName(UUID actor, UUID target, String displayName, String expectedDisplayName) {
    access.requireAdministrator(actor);
    String normalized = AdminUserRules.displayName(displayName);
    if (target == null || expectedDisplayName == null || expectedDisplayName.isBlank()
        || expectedDisplayName.length() > 200)
      throw new IllegalArgumentException("The current display name is required");
    var current = users.lockForDisplayNameUpdate(target);
    if (!current.displayName().equals(expectedDisplayName)) throw new DisplayNameConflictException();
    // A no-op is idempotent and does not manufacture an audit record for a change that never happened.
    if (current.displayName().equals(normalized)) return current;
    users.updateDisplayName(target, expectedDisplayName, normalized);
    users.auditDisplayNameChange(actor, target);
    return new AdminUser(target, current.email(), normalized, current.accountStatus(), current.roles());
  }
}
