package sg.edu.nus.serms.identity.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first ACTIVE administrator when none exists (#41). There is no public endpoint:
 * the only input is {@link FirstAdminProperties}, read once at startup. Invalid settings stop the
 * start with a message that never contains the password.
 */
@Service
public class FirstAdminBootstrap {
  public enum Outcome { NOT_REQUESTED, CREATED, ADMIN_EXISTS }

  static final int MIN_PASSWORD_LENGTH = 12;
  /** BCrypt, the default encoding, only uses the first 72 bytes and Spring refuses longer input. */
  static final int MAX_PASSWORD_BYTES = 72;
  /** Mirrors the database rule: lower case, trimmed, something@something.something. */
  private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
  /** Serializes concurrent starts of several application instances on the same database. */
  private static final long LOCK_KEY = 0x5345524d5341444dL;
  private static final Logger log = LoggerFactory.getLogger(FirstAdminBootstrap.class);

  private final JdbcTemplate jdbc;
  private final PasswordEncoder passwords;

  public FirstAdminBootstrap(JdbcTemplate jdbc, PasswordEncoder passwords) {
    this.jdbc = jdbc;
    this.passwords = passwords;
  }

  @Transactional
  public Outcome run(FirstAdminProperties settings) {
    if (!settings.requested()) return Outcome.NOT_REQUESTED;
    String email = settings.email().strip().toLowerCase(Locale.ROOT);
    String displayName = settings.displayName() == null ? "" : settings.displayName().strip();
    String password = settings.password() == null ? "" : settings.password();
    validate(email, displayName, password);

    jdbc.query("SELECT pg_advisory_xact_lock(?)", rs -> null, LOCK_KEY);
    if (activeAdminExists()) {
      log.info("First-admin bootstrap skipped: an active administrator already exists."
          + " Remove the serms.bootstrap.admin settings.");
      return Outcome.ADMIN_EXISTS;
    }
    if (Boolean.TRUE.equals(jdbc.queryForObject(
        "SELECT EXISTS (SELECT 1 FROM serms.app_user WHERE email = ?)", Boolean.class, email))) {
      throw new IllegalStateException("First-admin bootstrap: serms.bootstrap.admin.email belongs to an"
          + " existing account. Choose an address that is not in use.");
    }

    UUID userId = UUID.randomUUID();
    jdbc.update("""
        INSERT INTO serms.app_user(user_id, email, display_name, password_hash, account_status)
        VALUES (?, ?, ?, ?, 'ACTIVE')
        """, userId, email, displayName, passwords.encode(password));
    jdbc.update("""
        INSERT INTO serms.user_role(user_id, role_id)
        SELECT ?, role_id FROM serms.role WHERE code = 'ADMIN'
        """, userId);
    jdbc.update("""
        INSERT INTO serms.audit_log
          (audit_id, actor_id, action, entity_type, entity_id, outcome, request_id, change_summary)
        VALUES (?, NULL, 'USER_BOOTSTRAP_ADMIN', 'User', ?, 'SUCCESS', ?, ?)
        """, UUID.randomUUID(), userId, "bootstrap:" + userId,
        "First ACTIVE administrator created from startup configuration");
    log.info("First-admin bootstrap created the administrator {}. Remove the serms.bootstrap.admin"
        + " settings before the next start.", email);
    return Outcome.CREATED;
  }

  private boolean activeAdminExists() {
    return Boolean.TRUE.equals(jdbc.queryForObject("""
        SELECT EXISTS (SELECT 1 FROM serms.app_user u
          JOIN serms.user_role ur ON ur.user_id = u.user_id
          JOIN serms.role r ON r.role_id = ur.role_id
          WHERE r.code = 'ADMIN' AND u.account_status = 'ACTIVE')
        """, Boolean.class));
  }

  static void validate(String email, String displayName, String password) {
    if (!EMAIL.matcher(email).matches()) {
      throw new IllegalStateException("First-admin bootstrap: serms.bootstrap.admin.email is not a valid address.");
    }
    if (displayName.isEmpty()) {
      throw new IllegalStateException("First-admin bootstrap: serms.bootstrap.admin.display-name is required.");
    }
    if (password.strip().length() < MIN_PASSWORD_LENGTH) {
      throw new IllegalStateException("First-admin bootstrap: serms.bootstrap.admin.password must have at least "
          + MIN_PASSWORD_LENGTH + " characters.");
    }
    if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
      throw new IllegalStateException("First-admin bootstrap: serms.bootstrap.admin.password must be at most "
          + MAX_PASSWORD_BYTES + " bytes.");
    }
  }
}
