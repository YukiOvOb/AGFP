package sg.edu.nus.serms.identity.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import sg.edu.nus.serms.identity.domain.AdminUser;
import sg.edu.nus.serms.identity.repository.AdminUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminSecurityServiceTest {
  final AdminUserRepository repository = mock(AdminUserRepository.class);
  final AdminAccess access = mock(AdminAccess.class);
  final PasswordEncoder passwords = mock(PasswordEncoder.class);
  final AdminUserService service = new AdminUserService(repository, access, passwords);
  final UUID actor = UUID.randomUUID(), target = UUID.randomUUID();
  AdminUser target(String status, AdminUser.Role role) {
    var user = new AdminUser(target, "u@example.test", "User", status, Set.of(role), 2L);
    when(repository.lockForDisplayNameUpdate(target)).thenReturn(user);
    return user;
  }

  @Test void rolesStatusAndPasswordChangesAdvanceVersionAndAuditAfterWrites() {
    target("ACTIVE", AdminUser.Role.BORROWER);
    var result = service.updateRoles(actor, target, Set.of(AdminUser.Role.APPROVER), 2L);
    assertThat(result.version()).isEqualTo(3);
    var order = inOrder(repository, access);
    order.verify(repository).lockAdministratorChanges();
    order.verify(access).requireAdministrator(actor);
    order.verify(repository).lockForDisplayNameUpdate(target);
    order.verify(repository).replaceRoles(target, Set.of(AdminUser.Role.APPROVER));
    order.verify(repository).advanceSessionVersion(target, 2L);
    order.verify(repository).auditSecurityChange(actor, target, "USER_ROLES_CHANGED");
    service.updateStatus(actor, target, false, 2L);
    verify(repository).auditSecurityChange(actor, target, "USER_DEACTIVATED");
    when(passwords.encode("correct horse battery")).thenReturn("ENCODED");
    service.resetPassword(actor, target, "correct horse battery", 2L);
    verify(repository).resetPassword(target, "ENCODED");
    verify(repository).auditSecurityChange(actor, target, "USER_PASSWORD_RESET");
  }

  @Test void unchangedRolesAndStatusDoNotRevokeOrAudit() {
    var original = target("ACTIVE", AdminUser.Role.BORROWER);
    assertThat(service.updateRoles(actor, target, original.roles(), 2L)).isEqualTo(original);
    assertThat(service.updateStatus(actor, target, true, 2L)).isEqualTo(original);
    verify(repository, never()).advanceSessionVersion(any(), anyLong());
    verify(repository, never()).auditSecurityChange(any(), any(), anyString());
  }

  @Test void rejectsStaleOrNegativeVersionsBeforeWrites() {
    target("ACTIVE", AdminUser.Role.BORROWER);
    assertThatThrownBy(() -> service.updateStatus(actor, target, false, 1L))
        .isInstanceOf(AccountChangeConflictException.class);
    assertThatThrownBy(() -> service.resetPassword(actor, target, "correct horse battery", -1L))
        .isInstanceOf(IllegalArgumentException.class);
    verify(repository, never()).advanceSessionVersion(any(), anyLong());
    verifyNoInteractions(passwords);
  }

  @Test void forbidsSelfDeactivation() {
    when(repository.lockForDisplayNameUpdate(actor)).thenReturn(
        new AdminUser(actor, "a@example.test", "Admin", "ACTIVE", Set.of(AdminUser.Role.ADMIN), 0));
    assertThatThrownBy(() -> service.updateStatus(actor, actor, false, 0))
        .isInstanceOfSatisfying(AccountChangeConflictException.class,
            e -> assertThat(e.reason()).isEqualTo(AccountChangeConflictException.Reason.SELF_DEACTIVATION));
    verify(repository, never()).updateStatus(any(), anyString());
  }

  @Test void refusesDemotingOrDisablingFinalActiveAdministrator() {
    target("ACTIVE", AdminUser.Role.ADMIN);
    when(repository.activeAdministratorCount()).thenReturn(1L);
    assertThatThrownBy(() -> service.updateRoles(actor, target, Set.of(AdminUser.Role.BORROWER), 2))
        .isInstanceOfSatisfying(AccountChangeConflictException.class,
            e -> assertThat(e.reason()).isEqualTo(AccountChangeConflictException.Reason.LAST_ACTIVE_ADMIN));
    assertThatThrownBy(() -> service.updateStatus(actor, target, false, 2))
        .isInstanceOf(AccountChangeConflictException.class);
    verify(repository, never()).advanceSessionVersion(any(), anyLong());
  }

  @Test void canRemoveAdminFromDisabledAccountOrWhenAnotherActiveAdministratorExists() {
    target("DISABLED", AdminUser.Role.ADMIN);
    service.updateRoles(actor, target, Set.of(AdminUser.Role.BORROWER), 2);
    verify(repository, never()).activeAdministratorCount();
    target("ACTIVE", AdminUser.Role.ADMIN);
    when(repository.activeAdministratorCount()).thenReturn(2L);
    service.updateRoles(actor, target, Set.of(AdminUser.Role.BORROWER), 2);
    verify(repository, times(2)).advanceSessionVersion(target, 2);
  }

  @Test void passwordResetAlwaysRevokesAndDoesNotLeakViaDto() {
    target("DISABLED", AdminUser.Role.BORROWER);
    service.resetPassword(actor, target, "correct horse battery", 2);
    verify(repository).advanceSessionVersion(target, 2);
    assertThat(new sg.edu.nus.serms.identity.api.AdminUserController.ResetPasswordRequest(
        "secret-password", 0L).toString()).doesNotContain("secret-password");
  }

  @Test void deniedCurrentActorIsRecheckedUnderGlobalLockBeforeTargetLookup() {
    doThrow(new org.springframework.security.access.AccessDeniedException("demoted"))
        .when(access).requireAdministrator(actor);
    assertThatThrownBy(() -> service.updateRoles(actor, target, Set.of(AdminUser.Role.ADMIN), 0))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    verify(repository).lockAdministratorChanges();
    verify(repository, never()).lockForDisplayNameUpdate(any());
  }
}
