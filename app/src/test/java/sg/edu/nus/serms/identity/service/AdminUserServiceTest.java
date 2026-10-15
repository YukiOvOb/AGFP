package sg.edu.nus.serms.identity.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import sg.edu.nus.serms.identity.domain.AdminUser;
import sg.edu.nus.serms.identity.domain.AdminUserRules;
import sg.edu.nus.serms.identity.repository.AdminUserRepository;

class AdminUserServiceTest {
  final AdminUserRepository users = mock(AdminUserRepository.class);
  final AdminAccess access = mock(AdminAccess.class);
  final PasswordEncoder passwords = mock(PasswordEncoder.class);
  final AdminUserService service = new AdminUserService(users, access, passwords);
  final UUID actor = UUID.randomUUID();

  @Test void normalizesNamesButDoesNotChangeThePasswordOrImplicitlyGrantRoles() {
    when(passwords.encode("  correct horse battery")).thenReturn("ENCODED");
    var result = service.create(actor, " Person@EXAMPLE.test ", " Person ", "  correct horse battery",
        Set.of(AdminUser.Role.ADMIN));
    assertThat(result.email()).isEqualTo("person@example.test");
    assertThat(result.displayName()).isEqualTo("Person");
    assertThat(result.roles()).containsExactly(AdminUser.Role.ADMIN);
    verify(users).create(result.userId(), "person@example.test", "Person", "ENCODED", Set.of(AdminUser.Role.ADMIN));
    verify(users).auditCreation(actor, result.userId());
  }

  @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"short", "            ", "ééééééééééééééééééééééééééééééééééééé"})
  void rejectsWeakOrTooManyUtf8BytesWithoutEncodingOrWrites(String password) {
    assertThatThrownBy(() -> service.create(actor, "p@example.test", "P", password,
        Set.of(AdminUser.Role.BORROWER))).isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(passwords, users);
  }

  @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"invalid", "a@b", "a b@example.test"})
  void rejectsBadEmails(String email) {
    assertThatThrownBy(() -> AdminUserRules.email(email)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test void validationCoversDisplayNameRolesAndPagination() {
    for (String name : new String[]{null, " ", "x".repeat(201)})
      assertThatThrownBy(() -> AdminUserRules.displayName(name)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> AdminUserRules.roles(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> AdminUserRules.roles(Set.of())).isInstanceOf(IllegalArgumentException.class);
    var withNull = new java.util.HashSet<AdminUser.Role>(); withNull.add(null);
    assertThatThrownBy(() -> AdminUserRules.roles(withNull)).isInstanceOf(IllegalArgumentException.class);
    for (int[] pair : new int[][]{{-1,20},{100001,20},{0,0},{0,101}})
      assertThatThrownBy(() -> AdminUserRules.page(pair[0], pair[1])).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> AdminUserRules.search("x".repeat(201))).isInstanceOf(IllegalArgumentException.class);
    assertThat(AdminUserRules.search(null)).isEmpty();
  }

  @Test void checksCurrentDatabasePermissionBeforeAnyWork() {
    doThrow(new AccessDeniedException("disabled")).when(access).requireAdministrator(actor);
    assertThatThrownBy(() -> service.create(actor, "p@example.test", "P", "correct horse battery",
        Set.of(AdminUser.Role.BORROWER))).isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(users, passwords);
  }

  @Test void auditFailurePropagatesInsteadOfReturningSuccess() {
    doThrow(new IllegalStateException("unavailable")).when(users).auditCreation(eq(actor), any());
    assertThatThrownBy(() -> service.create(actor, "p@example.test", "P", "correct horse battery",
        Set.of(AdminUser.Role.BORROWER))).isInstanceOf(IllegalStateException.class);
  }

  @Test void listIsBoundedAndNormalized() {
    service.list(actor, "  percent%  ", 1, 20);
    verify(users).list("percent%", 1, 20);
  }

  @Test void currentAdminCheckRejectsMissingDisabledOrDemotedOperators() {
    var realAccess = new AdminAccess(users);
    assertThatThrownBy(() -> realAccess.requireAdministrator(null)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> realAccess.requireAdministrator(actor)).isInstanceOf(AccessDeniedException.class);
    when(users.isActiveAdministrator(actor)).thenReturn(true);
    realAccess.requireAdministrator(actor);
  }

  @Test void updatesOnlyTheNameAndAuditsItAfterTheConditionalWrite() {
    var target = new AdminUser(UUID.randomUUID(), "p@example.test", "Original", "DISABLED",
        Set.of(AdminUser.Role.BORROWER, AdminUser.Role.APPROVER));
    when(users.lockForDisplayNameUpdate(target.userId())).thenReturn(target);
    var updated = service.updateDisplayName(actor, target.userId(), " Renamed ", "Original");
    assertThat(updated).isEqualTo(new AdminUser(target.userId(), target.email(), "Renamed",
        target.accountStatus(), target.roles()));
    var order = inOrder(access, users);
    order.verify(access).requireAdministrator(actor);
    order.verify(users).lockForDisplayNameUpdate(target.userId());
    order.verify(users).updateDisplayName(target.userId(), "Original", "Renamed");
    order.verify(users).auditDisplayNameChange(actor, target.userId());
    verifyNoInteractions(passwords);
  }

  @Test void staleNameDoesNotWriteOrAuditEvenWhenTheDesiredNameAlreadyMatches() {
    UUID target = UUID.randomUUID();
    when(users.lockForDisplayNameUpdate(target)).thenReturn(
        new AdminUser(target, "p@example.test", "Renamed", "ACTIVE", Set.of(AdminUser.Role.ADMIN)));
    assertThatThrownBy(() -> service.updateDisplayName(actor, target, "Renamed", "Original"))
        .isInstanceOf(DisplayNameConflictException.class);
    verify(users, never()).updateDisplayName(any(), any(), any());
    verify(users, never()).auditDisplayNameChange(any(), any());
  }

  @Test void unchangedNameDoesNotManufactureAnAudit() {
    UUID target = UUID.randomUUID();
    var current = new AdminUser(target, "p@example.test", "Original", "ACTIVE", Set.of(AdminUser.Role.ADMIN));
    when(users.lockForDisplayNameUpdate(target)).thenReturn(current);
    assertThat(service.updateDisplayName(actor, target, " Original ", "Original")).isEqualTo(current);
    verify(users, never()).updateDisplayName(any(), any(), any());
    verify(users, never()).auditDisplayNameChange(any(), any());
  }

  @Test void validatesNamesAndIdentifierWithoutLookingUpTheTarget() {
    for (String invalid : new String[]{null, " ", "x".repeat(201)}) {
      assertThatThrownBy(() -> service.updateDisplayName(actor, actor, invalid, "Old"))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> service.updateDisplayName(actor, actor, "New", invalid))
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThatThrownBy(() -> service.updateDisplayName(actor, null, "New", "Old"))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(users, passwords);
  }

  @Test void permissionCheckPrecedesNameValidationAndTargetLookup() {
    doThrow(new AccessDeniedException("disabled")).when(access).requireAdministrator(actor);
    assertThatThrownBy(() -> service.updateDisplayName(actor, actor, null, null))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(users, passwords);
  }
}
