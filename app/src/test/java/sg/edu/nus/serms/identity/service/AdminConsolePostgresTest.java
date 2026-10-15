package sg.edu.nus.serms.identity.service;

import static org.assertj.core.api.Assertions.*;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import sg.edu.nus.serms.audit.repository.AuditQueryRepository;
import sg.edu.nus.serms.audit.service.AuditQueryService;
import sg.edu.nus.serms.identity.domain.AdminUser;
import sg.edu.nus.serms.identity.repository.AdminUserRepository;
import sg.edu.nus.serms.shared.security.SermsUserPrincipal;
import sg.edu.nus.serms.testsupport.SermsFixtures;
import sg.edu.nus.serms.testsupport.SermsPostgres;

/** Real PostgreSQL and actual Spring transaction/method-security proxies; no table clearing. */
class AdminConsolePostgresTest {
  static AnnotationConfigApplicationContext context;
  static JdbcTemplate jdbc;
  static AdminUserService users;
  static AuditQueryService audit;
  UUID admin;

  @Configuration @EnableTransactionManagement @EnableMethodSecurity
  @Import({AdminUserRepository.class, AdminAccess.class, AdminUserService.class,
      AuditQueryRepository.class, AuditQueryService.class})
  static class Config {
    @Bean DataSource dataSource() { return SermsPostgres.dataSource(); }
    @Bean JdbcTemplate jdbcTemplate(DataSource ds) { return new JdbcTemplate(ds); }
    @Bean DataSourceTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
    @Bean PasswordEncoder passwordEncoder() { return PasswordEncoderFactories.createDelegatingPasswordEncoder(); }
  }

  @BeforeAll static void start() {
    context = new AnnotationConfigApplicationContext(Config.class);
    jdbc = context.getBean(JdbcTemplate.class);
    users = context.getBean(AdminUserService.class);
    audit = context.getBean(AuditQueryService.class);
  }
  @AfterAll static void close() { if (context != null) context.close(); }
  @BeforeEach void signIn() {
    admin = new SermsFixtures(jdbc).user("ADMIN");
    authenticate(admin, "ADMIN");
  }
  @AfterEach void signOut() { SecurityContextHolder.clearContext(); }

  static void authenticate(UUID id, String role) {
    var principal = new SermsUserPrincipal(id, "operator@example.test", "Operator", Set.of(role));
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
  }

  @Test void persistsMultipleRolesEncodedPasswordAndOneSecretFreeAuditInOneTransaction() {
    String email = UUID.randomUUID() + "@example.test";
    var created = users.create(admin, email.toUpperCase(), " Person ", "correct horse battery",
        Set.of(AdminUser.Role.BORROWER, AdminUser.Role.APPROVER));
    assertThat(users.list(admin, email, 0, 20).content()).containsExactly(created);
    var row = jdbc.queryForMap("SELECT password_hash, notification_principal FROM serms.app_user WHERE user_id = ?", created.userId());
    assertThat(context.getBean(PasswordEncoder.class).matches("correct horse battery", (String) row.get("password_hash"))).isTrue();
    assertThat(row.get("notification_principal")).isEqualTo(created.userId().toString());
    var entries = audit.list(admin, admin, "USER_CREATED", "User", null, null, 0, 20);
    assertThat(entries.content()).singleElement().satisfies(entry -> {
      assertThat(entry.entityId()).isEqualTo(created.userId());
      assertThat(entry.actorId()).isEqualTo(admin);
      assertThat(entry.occurredAt()).isNotNull();
      assertThat(entry.changeSummary()).doesNotContain("correct horse battery", (String) row.get("password_hash"));
    });
  }

  @Test void duplicateCaseInsensitiveEmailIs409DomainConflictWithoutSecondAccountOrAudit() {
    String email = UUID.randomUUID() + "@example.test";
    users.create(admin, email, "First", "correct horse battery", Set.of(AdminUser.Role.BORROWER));
    assertThatThrownBy(() -> users.create(admin, " " + email.toUpperCase() + " ", "Second",
        "correct horse battery", Set.of(AdminUser.Role.ADMIN))).isInstanceOf(EmailAlreadyUsedException.class);
    assertThat(users.list(admin, email, 0, 20).content()).hasSize(1);
    assertThat(audit.list(admin, admin, "USER_CREATED", "User", null, null, 0, 20).totalElements()).isOne();
  }

  @Test void auditInsertFailureRollsBackAccountAndInitialRoleRows() {
    String email = UUID.randomUUID() + "@example.test";
    // Force an audit FK failure after writing the account/roles, inside the same transaction.
    var transactions = new TransactionTemplate(context.getBean(DataSourceTransactionManager.class));
    UUID missingActor = UUID.randomUUID();
    assertThatThrownBy(() -> transactions.execute(status -> {
      // The new account is valid; using a nonexistent audit actor forces the final FK write to fail.
      UUID id = UUID.randomUUID();
      var repository = context.getBean(AdminUserRepository.class);
      repository.create(id, email, "Rollback", "TEST_ONLY_HASH", Set.of(AdminUser.Role.BORROWER));
      repository.auditCreation(missingActor, id);
      return id;
    })).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(users.list(admin, email, 0, 20).totalElements()).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM serms.audit_log WHERE actor_id = ?", Integer.class, missingActor)).isZero();
  }

  @Test void serviceAuditFailureRollsBackEvenWithActualTransactionProxy() {
    String email = UUID.randomUUID() + "@example.test";
    var transactions = new TransactionTemplate(context.getBean(DataSourceTransactionManager.class));
    // An isolated transaction-local trigger targets only this actor; it is rolled back with the TX.
    assertThatThrownBy(() -> transactions.execute(status -> {
      jdbc.execute("CREATE FUNCTION pg_temp.reject_admin_audit() RETURNS trigger LANGUAGE plpgsql AS $$"
          + " BEGIN IF NEW.actor_id = '" + admin + "'::uuid THEN RAISE EXCEPTION 'test audit sink failure'; END IF; RETURN NEW; END $$");
      String trigger = "admin_test_" + admin.toString().replace("-", "");
      jdbc.execute("CREATE TRIGGER " + trigger + " BEFORE INSERT ON serms.audit_log FOR EACH ROW EXECUTE FUNCTION pg_temp.reject_admin_audit()");
      return users.create(admin, email, "Rollback", "correct horse battery", Set.of(AdminUser.Role.BORROWER));
    })).isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThat(users.list(admin, email, 0, 20).totalElements()).isZero();
  }

  @Test void concurrentCreationOfTheSameEmailHasExactlyOneWinnerAndAudit() throws Exception {
    String email = UUID.randomUUID() + "@example.test";
    var pool = Executors.newFixedThreadPool(2);
    var ready = new CountDownLatch(2); var go = new CountDownLatch(1);
    Callable<String> attempt = () -> {
      authenticate(admin, "ADMIN"); ready.countDown();
      try {
        if (!go.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
        users.create(admin, email, "Concurrent", "correct horse battery", Set.of(AdminUser.Role.BORROWER));
        return "created";
      } catch (EmailAlreadyUsedException expected) { return "duplicate"; }
      finally { SecurityContextHolder.clearContext(); }
    };
    try {
      var first = pool.submit(attempt); var second = pool.submit(attempt);
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); go.countDown();
      assertThat(java.util.List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder("created", "duplicate");
      assertThat(users.list(admin, email, 0, 20).totalElements()).isOne();
      assertThat(audit.list(admin, admin, "USER_CREATED", "User", null, null, 0, 20).totalElements()).isOne();
    } finally { go.countDown(); pool.shutdownNow(); }
  }

  @Test void literalSearchDoesNotTreatWildcardsOrSqlAsQuerySyntaxAndPagingIsStable() {
    String marker = UUID.randomUUID().toString();
    for (int i = 0; i < 3; i++)
      users.create(admin, marker + i + "@example.test", "literal%_! " + marker,
          "correct horse battery", Set.of(AdminUser.Role.BORROWER));
    assertThat(users.list(admin, "literal%_! " + marker, 0, 2).content()).hasSize(2);
    assertThat(users.list(admin, "literal%_! " + marker, 1, 2).content()).hasSize(1);
    assertThat(users.list(admin, "' OR 1=1 --", 0, 20).content()).isEmpty();
  }

  @Test void auditHasStablePagingHalfOpenTimeFiltersAndAppendOnlyProtection() {
    String email = UUID.randomUUID() + "@example.test";
    var created = users.create(admin, email, "Time filter", "correct horse battery", Set.of(AdminUser.Role.ADMIN));
    var entry = audit.list(admin, admin, "USER_CREATED", "User", null, null, 0, 1).content().get(0);
    assertThat(audit.list(admin, admin, "USER_CREATED", "User", entry.occurredAt(), entry.occurredAt().plusSeconds(1), 0, 20).totalElements()).isOne();
    assertThat(audit.list(admin, admin, "USER_CREATED", "User", null, entry.occurredAt(), 0, 20).totalElements()).isZero();
    assertThat(audit.list(admin, admin, "OTHER", "User", null, null, 0, 20).content()).isEmpty();
    assertThatThrownBy(() -> jdbc.update("UPDATE serms.audit_log SET outcome='CHANGED' WHERE entity_id=?", created.userId()))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThatThrownBy(() -> jdbc.update("DELETE FROM serms.audit_log WHERE entity_id=?", created.userId()))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }

  @Test void disabledOrDemotedSessionCannotUseNewAdminServices() {
    jdbc.update("UPDATE serms.app_user SET account_status='DISABLED' WHERE user_id=?", admin);
    assertThatThrownBy(() -> users.list(admin, "", 0, 20)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    assertThatThrownBy(() -> audit.list(admin, null, "", "", null, null, 0, 20)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }

  @Test void renameChangesOnlyNameAndCreatesOneAuditWithoutSecretsOrProfileText() {
    UUID target = new SermsFixtures(jdbc).user("BORROWER", "DISABLED");
    var before = jdbc.queryForMap("SELECT * FROM serms.app_user WHERE user_id=?", target);
    var rolesBefore = jdbc.queryForList("SELECT role_id FROM serms.user_role WHERE user_id=?", UUID.class, target);
    var result = users.updateDisplayName(admin, target, "  Renamed person  ", (String) before.get("display_name"));
    var after = jdbc.queryForMap("SELECT * FROM serms.app_user WHERE user_id=?", target);
    assertThat(result.displayName()).isEqualTo("Renamed person");
    assertThat(after.remove("display_name")).isEqualTo("Renamed person");
    before.remove("display_name");
    assertThat(after).isEqualTo(before);
    assertThat(jdbc.queryForList("SELECT role_id FROM serms.user_role WHERE user_id=?", UUID.class, target)).isEqualTo(rolesBefore);
    var entries = audit.list(admin, admin, "USER_DISPLAY_NAME_CHANGED", "User", null, null, 0, 20);
    assertThat(entries.content()).singleElement().satisfies(entry -> {
      assertThat(entry.entityId()).isEqualTo(target);
      assertThat(entry.changeSummary()).doesNotContain("Renamed person", "TEST_ONLY_NOT_A_LOGIN_HASH");
    });
  }

  @Test void noOpRenameAndStaleRenameDoNotCreateExtraAuditRows() {
    UUID target = new SermsFixtures(jdbc).user("BORROWER");
    users.updateDisplayName(admin, target, " UC01 test user ", "UC01 test user");
    assertThat(audit.list(admin, admin, "USER_DISPLAY_NAME_CHANGED", "User", null, null, 0, 20).totalElements()).isZero();
    users.updateDisplayName(admin, target, "Changed", "UC01 test user");
    assertThatThrownBy(() -> users.updateDisplayName(admin, target, "Stale edit", "UC01 test user"))
        .isInstanceOf(DisplayNameConflictException.class);
    assertThat(jdbc.queryForObject("SELECT display_name FROM serms.app_user WHERE user_id=?", String.class, target))
        .isEqualTo("Changed");
    assertThat(audit.list(admin, admin, "USER_DISPLAY_NAME_CHANGED", "User", null, null, 0, 20).totalElements()).isOne();
  }

  @Test void renameOfUnknownAccountIs404WithoutAudit() {
    assertThatThrownBy(() -> users.updateDisplayName(admin, UUID.randomUUID(), "New", "Old"))
        .isInstanceOf(sg.edu.nus.serms.shared.domain.RecordNotFoundException.class);
    assertThat(audit.list(admin, admin, "USER_DISPLAY_NAME_CHANGED", "User", null, null, 0, 20).totalElements()).isZero();
  }

  @Test void auditFailureRollsBackTheRenamedDisplayName() {
    UUID target = new SermsFixtures(jdbc).user("BORROWER");
    var transactions = new TransactionTemplate(context.getBean(DataSourceTransactionManager.class));
    assertThatThrownBy(() -> transactions.execute(status -> {
      jdbc.execute("CREATE FUNCTION pg_temp.reject_name_audit() RETURNS trigger LANGUAGE plpgsql AS $$"
          + " BEGIN IF NEW.entity_id = '" + target + "'::uuid AND NEW.action='USER_DISPLAY_NAME_CHANGED' THEN "
          + " RAISE EXCEPTION 'test name audit failure'; END IF; RETURN NEW; END $$");
      jdbc.execute("CREATE TRIGGER name_test_" + target.toString().replace("-", "")
          + " BEFORE INSERT ON serms.audit_log FOR EACH ROW EXECUTE FUNCTION pg_temp.reject_name_audit()");
      return users.updateDisplayName(admin, target, "Must roll back", "UC01 test user");
    })).isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThat(jdbc.queryForObject("SELECT display_name FROM serms.app_user WHERE user_id=?", String.class, target))
        .isEqualTo("UC01 test user");
    assertThat(audit.list(admin, admin, "USER_DISPLAY_NAME_CHANGED", "User", null, null, 0, 20).totalElements()).isZero();
  }

  @Test void twoEditorsUsingTheSameOldNameHaveOneWinnerAndOneConflict() throws Exception {
    UUID target = new SermsFixtures(jdbc).user("BORROWER");
    var pool = Executors.newFixedThreadPool(2);
    var ready = new CountDownLatch(2); var go = new CountDownLatch(1);
    java.util.function.Function<String, Callable<String>> attempt = newName -> () -> {
      authenticate(admin, "ADMIN"); ready.countDown();
      try {
        if (!go.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
        users.updateDisplayName(admin, target, newName, "UC01 test user");
        return "updated";
      } catch (DisplayNameConflictException expected) { return "conflict"; }
      finally { SecurityContextHolder.clearContext(); }
    };
    try {
      var first = pool.submit(attempt.apply("Editor A")); var second = pool.submit(attempt.apply("Editor B"));
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); go.countDown();
      assertThat(java.util.List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder("updated", "conflict");
      assertThat(jdbc.queryForObject("SELECT display_name FROM serms.app_user WHERE user_id=?", String.class, target))
          .isIn("Editor A", "Editor B");
      assertThat(audit.list(admin, admin, "USER_DISPLAY_NAME_CHANGED", "User", null, null, 0, 20).totalElements()).isOne();
    } finally { go.countDown(); pool.shutdownNow(); }
  }

  @Test void currentAdminCanRenameThemselfWithoutChangingRoleOrAccountStatus() {
    var renamed = users.updateDisplayName(admin, admin, "Administrator renamed", "UC01 test user");
    assertThat(renamed.roles()).containsExactly(AdminUser.Role.ADMIN);
    assertThat(renamed.accountStatus()).isEqualTo("ACTIVE");
    // This slice deliberately does not refresh or invalidate an existing session Principal.
    assertThat(((SermsUserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal())
        .getDisplayName()).isEqualTo("Operator");
  }

  @Test void twoAdministratorsCanRenameEachOtherWithoutAuditForeignKeyDeadlock() throws Exception {
    UUID otherAdmin = new SermsFixtures(jdbc).user("ADMIN");
    var transactions = new TransactionTemplate(context.getBean(DataSourceTransactionManager.class));
    var pool = Executors.newFixedThreadPool(2);
    var ready = new CountDownLatch(2); var go = new CountDownLatch(1);
    java.util.function.BiFunction<UUID, UUID, Callable<AdminUser>> attempt = (operator, target) -> () -> {
      authenticate(operator, "ADMIN");
      try {
        return transactions.execute(status -> {
          context.getBean(AdminUserRepository.class).lockForDisplayNameUpdate(target);
          ready.countDown();
          try {
            if (!go.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
          } catch (InterruptedException failure) {
            Thread.currentThread().interrupt(); throw new IllegalStateException(failure);
          }
          return users.updateDisplayName(operator, target, "Renamed " + target, "UC01 test user");
        });
      } finally { SecurityContextHolder.clearContext(); }
    };
    try {
      var first = pool.submit(attempt.apply(admin, otherAdmin));
      var second = pool.submit(attempt.apply(otherAdmin, admin));
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); go.countDown();
      assertThat(first.get(20, TimeUnit.SECONDS).displayName()).isEqualTo("Renamed " + otherAdmin);
      assertThat(second.get(20, TimeUnit.SECONDS).displayName()).isEqualTo("Renamed " + admin);
      assertThat(audit.list(admin, admin, "USER_DISPLAY_NAME_CHANGED", "User", null, null, 0, 20).totalElements()).isOne();
      assertThat(audit.list(admin, otherAdmin, "USER_DISPLAY_NAME_CHANGED", "User", null, null, 0, 20).totalElements()).isOne();
    } finally { go.countDown(); pool.shutdownNow(); }
  }
}
