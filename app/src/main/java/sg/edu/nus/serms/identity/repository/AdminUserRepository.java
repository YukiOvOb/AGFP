package sg.edu.nus.serms.identity.repository;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import sg.edu.nus.serms.identity.domain.AdminUser;
import sg.edu.nus.serms.identity.service.EmailAlreadyUsedException;
import sg.edu.nus.serms.identity.service.DisplayNameConflictException;
import sg.edu.nus.serms.shared.domain.RecordNotFoundException;

@Repository
public class AdminUserRepository {
  private final JdbcTemplate jdbc;
  public AdminUserRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public boolean isActiveAdministrator(UUID id) {
    return Boolean.TRUE.equals(jdbc.queryForObject("""
        SELECT EXISTS (SELECT 1 FROM serms.app_user u
          JOIN serms.user_role ur USING (user_id) JOIN serms.role r USING (role_id)
          WHERE u.user_id = ? AND u.account_status = 'ACTIVE' AND r.code = 'ADMIN')
        """, Boolean.class, id));
  }

  public AdminUser.Page list(String search, int page, int size) {
    // Literal substring search: %, _ and ! in a name are not query wildcards.
    String pattern = "%" + search.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    String where = " WHERE (u.email ILIKE ? ESCAPE '!' OR u.display_name ILIKE ? ESCAPE '!')";
    long total = jdbc.queryForObject("SELECT count(*) FROM serms.app_user u" + where,
        Long.class, pattern, pattern);
    var rows = jdbc.query("""
        SELECT u.user_id, u.email, u.display_name, u.account_status, u.session_version,
          coalesce(string_agg(r.code, ',' ORDER BY r.code), '') AS roles
        FROM serms.app_user u
        LEFT JOIN serms.user_role ur USING (user_id) LEFT JOIN serms.role r USING (role_id)
        """ + where + " GROUP BY u.user_id ORDER BY u.email, u.user_id LIMIT ? OFFSET ?",
        (rs, row) -> new AdminUser(rs.getObject("user_id", UUID.class), rs.getString("email"),
            rs.getString("display_name"), rs.getString("account_status"),
            rs.getString("roles").isEmpty() ? Set.of() : new TreeSet<>(Arrays.stream(
                rs.getString("roles").split(",")).map(AdminUser.Role::valueOf).toList()), rs.getLong("session_version")),
        pattern, pattern, size, (long) page * size);
    return new AdminUser.Page(rows, total, page, size);
  }

  public void create(UUID id, String email, String displayName, String encodedPassword, Set<AdminUser.Role> roles) {
    try {
      jdbc.update("""
          INSERT INTO serms.app_user(user_id, email, display_name, password_hash, account_status)
          VALUES (?, ?, ?, ?, 'ACTIVE')
          """, id, email, displayName, encodedPassword);
    } catch (DuplicateKeyException failure) {
      if (failure.getMostSpecificCause() instanceof PSQLException postgres
          && postgres.getServerErrorMessage() != null
          && "app_user_email_key".equals(postgres.getServerErrorMessage().getConstraint()))
        throw new EmailAlreadyUsedException();
      throw failure;
    }
    for (var role : roles) {
      int changed = jdbc.update("""
          INSERT INTO serms.user_role(user_id, role_id)
          SELECT ?, role_id FROM serms.role WHERE code = ?
          """, id, role.name());
      if (changed != 1) throw new IllegalStateException("Configured role is missing");
    }
  }

  public void auditCreation(UUID actor, UUID target) {
    jdbc.update("""
        INSERT INTO serms.audit_log
          (audit_id, actor_id, action, entity_type, entity_id, outcome, request_id, change_summary)
        VALUES (?, ?, 'USER_CREATED', 'User', ?, 'SUCCESS', ?, 'Administrator created an active account and assigned its initial roles')
        """, UUID.randomUUID(), actor, target, "admin-user:" + UUID.randomUUID());
  }

  /** Serialize name edits without blocking audit FK key-share locks on administrator actors. */
  public AdminUser lockForDisplayNameUpdate(UUID target) {
    return jdbc.query("""
        SELECT u.user_id, u.email, u.display_name, u.account_status, u.session_version, r.code
        FROM serms.app_user u
        LEFT JOIN serms.user_role ur USING (user_id) LEFT JOIN serms.role r USING (role_id)
        WHERE u.user_id = ? FOR NO KEY UPDATE OF u
        """, rs -> {
          if (!rs.next()) throw new RecordNotFoundException();
          String email = rs.getString("email"), name = rs.getString("display_name"),
              status = rs.getString("account_status");
          long version = rs.getLong("session_version");
          var roles = new TreeSet<AdminUser.Role>();
          do {
            String role = rs.getString("code");
            if (role != null) roles.add(AdminUser.Role.valueOf(role));
          } while (rs.next());
          return new AdminUser(target, email, name, status, roles, version);
        }, target);
  }

  public void updateDisplayName(UUID target, String expectedName, String newName) {
    int changed = jdbc.update("""
        UPDATE serms.app_user SET display_name = ? WHERE user_id = ? AND display_name = ?
        """, newName, target, expectedName);
    if (changed != 1) throw new DisplayNameConflictException();
  }

  public void auditDisplayNameChange(UUID actor, UUID target) {
    jdbc.update("""
        INSERT INTO serms.audit_log
          (audit_id, actor_id, action, entity_type, entity_id, outcome, request_id, change_summary)
        VALUES (?, ?, 'USER_DISPLAY_NAME_CHANGED', 'User', ?, 'SUCCESS', ?, 'Administrator changed the account display name')
        """, UUID.randomUUID(), actor, target, "admin-user:" + UUID.randomUUID());
  }

  /** Same cross-instance lock as first-admin bootstrap; acquire BEFORE any target row locks. */
  public void lockAdministratorChanges() {
    jdbc.query("SELECT pg_advisory_xact_lock(?)", rs -> null, 0x5345524d5341444dL);
  }

  public long activeAdministratorCount() {
    return jdbc.queryForObject("""
        SELECT count(*) FROM serms.app_user u
        WHERE u.account_status='ACTIVE' AND EXISTS (
          SELECT 1 FROM serms.user_role ur JOIN serms.role r USING(role_id)
          WHERE ur.user_id=u.user_id AND r.code='ADMIN')
        """, Long.class);
  }

  public void updateStatus(UUID target, String status) {
    requireOne(jdbc.update("UPDATE serms.app_user SET account_status=? WHERE user_id=?", status, target));
  }

  public void replaceRoles(UUID target, Set<AdminUser.Role> roles) {
    jdbc.update("DELETE FROM serms.user_role WHERE user_id=?", target);
    for (var role : roles) requireOne(jdbc.update("""
        INSERT INTO serms.user_role(user_id,role_id) SELECT ?,role_id FROM serms.role WHERE code=?
        """, target, role.name()));
  }

  public void resetPassword(UUID target, String hash) {
    requireOne(jdbc.update("UPDATE serms.app_user SET password_hash=? WHERE user_id=?", hash, target));
  }

  public void advanceSessionVersion(UUID target, long expected) {
    requireOne(jdbc.update("""
        UPDATE serms.app_user SET session_version=session_version+1
        WHERE user_id=? AND session_version=?
        """, target, expected));
  }

  public void auditSecurityChange(UUID actor, UUID target, String action) {
    // Only fixed server-owned action names; no profile input, password or hash in the audit.
    jdbc.update("""
        INSERT INTO serms.audit_log
          (audit_id, actor_id, action, entity_type, entity_id, outcome, request_id, change_summary)
        VALUES (?, ?, ?, 'User', ?, 'SUCCESS', ?, 'Administrator changed account security settings and revoked old sessions')
        """, UUID.randomUUID(), actor, action, target, "admin-security:" + UUID.randomUUID());
  }

  private void requireOne(int changed) {
    if (changed != 1) throw new sg.edu.nus.serms.identity.service.AccountChangeConflictException(
        sg.edu.nus.serms.identity.service.AccountChangeConflictException.Reason.STALE_ACCOUNT);
  }
}
