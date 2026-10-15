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
    return new AdminUser(target, current.email(), normalized, current.accountStatus(), current.roles(), current.version());
  }

  @Transactional
  public AdminUser updateStatus(UUID actor, UUID target, boolean active, long expectedVersion) {
    var current = lockSecurityChange(actor, target, expectedVersion);
    String status = active ? "ACTIVE" : "DISABLED";
    if (!active && actor.equals(target))
      throw new AccountChangeConflictException(AccountChangeConflictException.Reason.SELF_DEACTIVATION);
    if (current.accountStatus().equals(status)) return current;
    protectLastAdministrator(current, active, current.roles());
    users.updateStatus(target, status);
    users.advanceSessionVersion(target, expectedVersion);
    users.auditSecurityChange(actor, target, active ? "USER_ACTIVATED" : "USER_DEACTIVATED");
    return new AdminUser(target, current.email(), current.displayName(), status, current.roles(), expectedVersion + 1);
  }

  @Transactional
  public AdminUser updateRoles(UUID actor, UUID target, Set<AdminUser.Role> roles, long expectedVersion) {
    // Authorization precedes validation/target lookup; copy caller-owned mutable data before using it.
    users.lockAdministratorChanges();
    access.requireAdministrator(actor);
    AdminUserRules.roles(roles);
    var requested = Set.copyOf(roles);
    var current = checkedTarget(target, expectedVersion);
    if (current.roles().equals(requested)) return current;
    protectLastAdministrator(current, current.accountStatus().equals("ACTIVE"), requested);
    users.replaceRoles(target, requested);
    users.advanceSessionVersion(target, expectedVersion);
    users.auditSecurityChange(actor, target, "USER_ROLES_CHANGED");
    return new AdminUser(target, current.email(), current.displayName(), current.accountStatus(), requested, expectedVersion + 1);
  }

  @Transactional
  public AdminUser resetPassword(UUID actor, UUID target, String password, long expectedVersion) {
    users.lockAdministratorChanges();
    access.requireAdministrator(actor);
    AdminUserRules.password(password);
    var current = checkedTarget(target, expectedVersion);
    users.resetPassword(target, passwords.encode(password));
    users.advanceSessionVersion(target, expectedVersion);
    users.auditSecurityChange(actor, target, "USER_PASSWORD_RESET");
    return new AdminUser(target, current.email(), current.displayName(), current.accountStatus(), current.roles(), expectedVersion + 1);
  }

  private AdminUser lockSecurityChange(UUID actor, UUID target, long expectedVersion) {
    users.lockAdministratorChanges();
    access.requireAdministrator(actor);
    return checkedTarget(target, expectedVersion);
  }

  private AdminUser checkedTarget(UUID target, long expectedVersion) {
    if (target == null || expectedVersion < 0) throw new IllegalArgumentException("Invalid account version");
    var current = users.lockForDisplayNameUpdate(target);
    if (current.version() != expectedVersion)
      throw new AccountChangeConflictException(AccountChangeConflictException.Reason.STALE_ACCOUNT);
    return current;
  }

  private void protectLastAdministrator(AdminUser current, boolean active, Set<AdminUser.Role> roles) {
    if (current.accountStatus().equals("ACTIVE") && current.roles().contains(AdminUser.Role.ADMIN)
        && (!active || !roles.contains(AdminUser.Role.ADMIN)) && users.activeAdministratorCount() <= 1)
      throw new AccountChangeConflictException(AccountChangeConflictException.Reason.LAST_ACTIVE_ADMIN);
  }
}
