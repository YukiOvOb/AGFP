package sg.edu.nus.serms.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import sg.edu.nus.serms.testsupport.SermsFixtures;
import sg.edu.nus.serms.testsupport.SermsPostgres;

/**
 * Real PostgreSQL. The shared test database already contains administrators from other tests, so
 * every case runs in one transaction that is rolled back, disabling the existing administrators
 * only inside it.
 */
class FirstAdminBootstrapPostgresTest {
  private static JdbcTemplate jdbc;
  private static TransactionTemplate transactions;
  private static SermsFixtures fixtures;
  private static final PasswordEncoder PASSWORDS = PasswordEncoderFactories.createDelegatingPasswordEncoder();
  private final FirstAdminBootstrap bootstrap = new FirstAdminBootstrap(jdbc, PASSWORDS);

  @BeforeAll
  static void database() {
    var dataSource = SermsPostgres.dataSource();
    jdbc = new JdbcTemplate(dataSource);
    transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    fixtures = new SermsFixtures(jdbc);
  }

  private static void inRolledBackTransaction(boolean withoutActiveAdmin, Consumer<String> test) {
    transactions.executeWithoutResult(status -> {
      if (withoutActiveAdmin) {
        jdbc.update("""
            UPDATE serms.app_user SET account_status = 'DISABLED' WHERE user_id IN (
              SELECT ur.user_id FROM serms.user_role ur JOIN serms.role r ON r.role_id = ur.role_id
              WHERE r.code = 'ADMIN')
            """);
      }
      test.accept("first-admin-" + UUID.randomUUID() + "@example.test");
      status.setRollbackOnly();
    });
  }

  private static int users(String email) {
    return jdbc.queryForObject("SELECT count(*) FROM serms.app_user WHERE email = ?", Integer.class, email);
  }

  @Test
  void createsOneActiveAdministratorWithAnEncodedPasswordAndAnAuditRow() {
    inRolledBackTransaction(true, email -> {
      var outcome = bootstrap.run(new FirstAdminProperties("  " + email.toUpperCase() + " ", " First Admin ",
          "correct horse battery"));

      assertThat(outcome).isEqualTo(FirstAdminBootstrap.Outcome.CREATED);
      var user = jdbc.queryForMap("SELECT user_id, display_name, password_hash, account_status"
          + " FROM serms.app_user WHERE email = ?", email);
      assertThat(user).containsEntry("display_name", "First Admin").containsEntry("account_status", "ACTIVE");
      assertThat(PASSWORDS.matches("correct horse battery", (String) user.get("password_hash"))).isTrue();
      assertThat((String) user.get("password_hash")).doesNotContain("correct horse battery");
      assertThat(jdbc.queryForList("SELECT r.code FROM serms.user_role ur JOIN serms.role r USING (role_id)"
          + " WHERE ur.user_id = ?", String.class, user.get("user_id"))).containsExactly("ADMIN");
      var audit = jdbc.queryForMap("SELECT actor_id, action, entity_type, outcome, change_summary"
          + " FROM serms.audit_log WHERE entity_id = ?", user.get("user_id"));
      assertThat(audit).containsEntry("action", "USER_BOOTSTRAP_ADMIN").containsEntry("entity_type", "User")
          .containsEntry("outcome", "SUCCESS").containsEntry("actor_id", null);
      assertThat((String) audit.get("change_summary")).doesNotContain("correct horse battery");

      // A second start with the same settings finds the new administrator and changes nothing.
      assertThat(bootstrap.run(new FirstAdminProperties(email, "First Admin", "correct horse battery")))
          .isEqualTo(FirstAdminBootstrap.Outcome.ADMIN_EXISTS);
      assertThat(users(email)).isOne();
    });
  }

  @Test
  void anExistingActiveAdministratorMeansNothingIsCreated() {
    inRolledBackTransaction(false, email -> {
      fixtures.user("ADMIN");

      assertThat(bootstrap.run(new FirstAdminProperties(email, "Second Admin", "correct horse battery")))
          .isEqualTo(FirstAdminBootstrap.Outcome.ADMIN_EXISTS);
      assertThat(users(email)).isZero();
    });
  }

  @Test
  void anEmailThatBelongsToAnotherAccountIsRefusedWithoutWrites() {
    inRolledBackTransaction(true, unused -> {
      UUID borrower = fixtures.user("BORROWER");
      String taken = jdbc.queryForObject("SELECT email FROM serms.app_user WHERE user_id = ?", String.class, borrower);
      int before = jdbc.queryForObject("SELECT count(*) FROM serms.audit_log WHERE action = 'USER_BOOTSTRAP_ADMIN'",
          Integer.class);

      assertThatThrownBy(() -> bootstrap.run(new FirstAdminProperties(taken, "Admin", "correct horse battery")))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("existing account");
      assertThat(jdbc.queryForList("SELECT r.code FROM serms.user_role ur JOIN serms.role r USING (role_id)"
          + " WHERE ur.user_id = ?", String.class, borrower)).containsExactly("BORROWER");
      assertThat(jdbc.queryForObject("SELECT count(*) FROM serms.audit_log WHERE action = 'USER_BOOTSTRAP_ADMIN'",
          Integer.class)).isEqualTo(before);
    });
  }
}
