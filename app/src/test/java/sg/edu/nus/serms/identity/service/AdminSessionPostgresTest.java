package sg.edu.nus.serms.identity.service;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import sg.edu.nus.serms.identity.domain.AdminUser;
import sg.edu.nus.serms.testsupport.SermsFixtures;
import sg.edu.nus.serms.testsupport.SermsPostgres;
import tools.jackson.databind.ObjectMapper;

/** Actual app/filter chain, password login, two independent sessions, transactions and PostgreSQL 18. */
@SpringBootTest(properties = {"spring.flyway.enabled=false", "serms.notifications.worker-enabled=false",
    "serms.security.secure-cookies=false", "server.servlet.session.cookie.secure=false"})
@AutoConfigureMockMvc
@Import(AdminSessionPostgresTest.Database.class)
class AdminSessionPostgresTest {
  @TestConfiguration static class Database {
    @Bean DataSource dataSource() { return SermsPostgres.dataSource(); }
  }
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired PasswordEncoder encoder;
  @Autowired ObjectMapper json;
  @Autowired AdminUserService users;
  @Autowired PlatformTransactionManager transactions;
  UUID admin;
  MockHttpSession operator;
  static final String PASSWORD = "session-test-password";

  @BeforeEach void fixture() throws Exception {
    admin = new SermsFixtures(jdbc).user("ADMIN");
    jdbc.update("UPDATE serms.app_user SET password_hash=? WHERE user_id=?", encoder.encode(PASSWORD), admin);
    operator = login(admin, PASSWORD);
  }
  @AfterEach void clear() { SecurityContextHolder.clearContext(); }

  RequestPostProcessor csrf() throws Exception {
    var cookie = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isNoContent())
        .andReturn().getResponse().getCookie("XSRF-TOKEN");
    assertThat(cookie).isNotNull();
    return request -> { request.setCookies(cookie); request.addHeader("X-XSRF-TOKEN", cookie.getValue()); return request; };
  }
  String body(Object value) { return json.writeValueAsString(value); }
  MockHttpSession login(UUID id, String password) throws Exception {
    var result = mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json")
        .content(body(Map.of("email", id + "@example.invalid", "password", password))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.passwordHash").doesNotExist())
        .andExpect(jsonPath("$.sessionVersion").doesNotExist()).andReturn();
    return (MockHttpSession) result.getRequest().getSession(false);
  }
  UUID borrower() {
    UUID id = new SermsFixtures(jdbc).user("BORROWER");
    jdbc.update("UPDATE serms.app_user SET password_hash=? WHERE user_id=?", encoder.encode(PASSWORD), id);
    return id;
  }
  String url(UUID id, String operation) { return "/api/v1/admin/users/" + id + "/" + operation; }
  void stale(MockHttpSession session) throws Exception {
    mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.type").value("/problems/unauthenticated"));
    assertThat(session.isInvalid()).isTrue();
  }
  long version(UUID id) { return jdbc.queryForObject("SELECT session_version FROM serms.app_user WHERE user_id=?", Long.class, id); }

  @Test void disableRevokesBothOldSessionsAndReenableDoesNotResurrectThem() throws Exception {
    UUID target = borrower();
    var first = login(target, PASSWORD); var second = login(target, PASSWORD);
    mvc.perform(patch(url(target, "status")).session(operator).with(csrf()).contentType("application/json")
        .content(body(Map.of("active", false, "expectedVersion", 0))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.accountStatus").value("DISABLED"))
        .andExpect(jsonPath("$.version").value(1));
    stale(first);
    mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json")
        .content(body(Map.of("email", target + "@example.invalid", "password", PASSWORD))))
        .andExpect(status().isUnauthorized());
    mvc.perform(patch(url(target, "status")).session(operator).with(csrf()).contentType("application/json")
        .content(body(Map.of("active", true, "expectedVersion", 1)))).andExpect(status().isOk());
    stale(second);
    mvc.perform(get("/api/v1/auth/me").session(login(target, PASSWORD))).andExpect(status().isOk());
    assertThat(version(target)).isEqualTo(2);
  }

  @Test void roleChangeRevokesBothSessionsThenFreshLoginGetsOnlyExplicitNewRoles() throws Exception {
    UUID target = borrower(); var first = login(target, PASSWORD); var second = login(target, PASSWORD);
    mvc.perform(patch(url(target, "roles")).session(operator).with(csrf()).contentType("application/json")
        .content(body(Map.of("roles", List.of("ADMIN"), "expectedVersion", 0))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
    stale(first); stale(second);
    var fresh = login(target, PASSWORD);
    mvc.perform(get("/api/v1/auth/me").session(fresh)).andExpect(jsonPath("$.roles.length()").value(1))
        .andExpect(jsonPath("$.roles[0]").value("ADMIN"));
    mvc.perform(get("/api/v1/approvals/pending").session(fresh)).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/admin/users").session(fresh)).andExpect(status().isOk());
  }

  @Test void resetPasswordRevokesAllSessionsAndOldPasswordFailsWithoutHashInSessionOrAudit() throws Exception {
    UUID target = borrower(); var first = login(target, PASSWORD); var second = login(target, PASSWORD);
    String replacement = "replacement-test-password";
    String response = mvc.perform(post(url(target, "password-reset")).session(operator).with(csrf())
        .contentType("application/json").content(body(Map.of("newPassword", replacement, "expectedVersion", 0))))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertThat(response).doesNotContain(replacement, "passwordHash", "{bcrypt}");
    stale(first); stale(second);
    mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json")
        .content(body(Map.of("email", target + "@example.invalid", "password", PASSWORD))))
        .andExpect(status().isUnauthorized());
    var fresh = login(target, replacement);
    var context = (org.springframework.security.core.context.SecurityContext)
        fresh.getAttribute("SPRING_SECURITY_CONTEXT");
    var principal = (sg.edu.nus.serms.shared.security.SermsUserPrincipal) context.getAuthentication().getPrincipal();
    assertThat(principal.getPassword()).isEmpty();
    assertThat(principal.getSessionVersion()).isOne();
    assertThat(jdbc.queryForObject("SELECT change_summary FROM serms.audit_log WHERE entity_id=? AND action='USER_PASSWORD_RESET'",
        String.class, target)).doesNotContain(PASSWORD, replacement, "{bcrypt}");
  }

  @Test void identicalRolesAndStatusDoNotInvalidateSessionsOrWriteFakeAudits() throws Exception {
    UUID target = borrower(); var session = login(target, PASSWORD);
    mvc.perform(patch(url(target, "roles")).session(operator).with(csrf()).contentType("application/json")
        .content(body(Map.of("roles", List.of("BORROWER"), "expectedVersion", 0)))).andExpect(status().isOk());
    mvc.perform(patch(url(target, "status")).session(operator).with(csrf()).contentType("application/json")
        .content(body(Map.of("active", true, "expectedVersion", 0)))).andExpect(status().isOk());
    mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isOk());
    assertThat(version(target)).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM serms.audit_log WHERE entity_id=?", Long.class, target)).isZero();
  }

  @Test void staleMutationIs409WithoutAnotherChangeAndSelfDeactivationIs409() throws Exception {
    UUID target = borrower();
    mvc.perform(patch(url(target, "status")).session(operator).with(csrf()).contentType("application/json")
        .content(body(Map.of("active", false, "expectedVersion", 0)))).andExpect(status().isOk());
    mvc.perform(patch(url(target, "roles")).session(operator).with(csrf()).contentType("application/json")
        .content(body(Map.of("roles", List.of("ADMIN"), "expectedVersion", 0))))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.type").value("/problems/account-version-conflict"));
    mvc.perform(patch(url(admin, "status")).session(operator).with(csrf()).contentType("application/json")
        .content(body(Map.of("active", false, "expectedVersion", 0))))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.type").value("/problems/self-deactivation"));
    assertThat(version(target)).isOne();
  }

  @Test void staleProtectedPostReturns401BeforeCsrfButPublicCsrfAndLogoutStayAvailable() throws Exception {
    UUID target = borrower(); var session = login(target, PASSWORD);
    mvc.perform(patch(url(target, "status")).session(operator).with(csrf()).contentType("application/json")
        .content(body(Map.of("active", false, "expectedVersion", 0)))).andExpect(status().isOk());
    mvc.perform(get("/api/v1/auth/csrf").session(session)).andExpect(status().isNoContent());
    mvc.perform(post("/api/v1/reservations").session(session).contentType("application/json").content("{}"))
        .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.type").value("/problems/unauthenticated"));
    var logoutSession = login(admin, PASSWORD);
    // Independent old session can still explicitly log out after revocation, with valid CSRF.
    jdbc.update("UPDATE serms.app_user SET session_version=session_version+1 WHERE user_id=?", admin);
    mvc.perform(post("/api/v1/auth/logout").session(logoutSession).with(csrf())).andExpect(status().isNoContent());
    mvc.perform(post("/api/v1/auth/logout").with(csrf())).andExpect(status().isNoContent());
  }

  @ParameterizedTest @ValueSource(strings={"status", "roles", "password-reset"})
  void auditFailureRollsBackSecurityDataVersionAndSessionValidity(String operation) throws Exception {
    UUID target = borrower(); var session = login(target, PASSWORD);
    var before = jdbc.queryForMap("SELECT * FROM serms.app_user WHERE user_id=?", target);
    var roles = jdbc.queryForList("SELECT role_id FROM serms.user_role WHERE user_id=?", UUID.class, target);
    AdminConsolePostgresTest.authenticate(admin, "ADMIN");
    assertThatThrownBy(() -> new TransactionTemplate(transactions).execute(status -> {
      jdbc.execute("CREATE FUNCTION pg_temp.reject_security_audit() RETURNS trigger LANGUAGE plpgsql AS $$"
          + " BEGIN IF NEW.entity_id='" + target + "'::uuid THEN RAISE EXCEPTION 'test audit failure'; END IF; RETURN NEW; END $$");
      jdbc.execute("CREATE TRIGGER security_test_" + target.toString().replace("-", "")
          + " BEFORE INSERT ON serms.audit_log FOR EACH ROW EXECUTE FUNCTION pg_temp.reject_security_audit()");
      return switch(operation) {
        case "status" -> users.updateStatus(admin, target, false, 0);
        case "roles" -> users.updateRoles(admin, target, Set.of(AdminUser.Role.APPROVER), 0);
        default -> users.resetPassword(admin, target, "replacement-test-password", 0);
      };
    })).isInstanceOf(org.springframework.dao.DataAccessException.class);
    SecurityContextHolder.clearContext();
    assertThat(jdbc.queryForMap("SELECT * FROM serms.app_user WHERE user_id=?", target)).isEqualTo(before);
    assertThat(jdbc.queryForList("SELECT role_id FROM serms.user_role WHERE user_id=?", UUID.class, target)).isEqualTo(roles);
    mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isOk());
    assertThat(jdbc.queryForObject("SELECT count(*) FROM serms.audit_log WHERE entity_id=?", Long.class, target)).isZero();
  }

  @Test void concurrentChangesAgainstOneVersionHaveOneWinnerOneConflictAndOneAudit() throws Exception {
    UUID target = borrower();
    var pool = Executors.newFixedThreadPool(2); var ready = new CountDownLatch(2); var go = new CountDownLatch(1);
    Callable<String> task = () -> {
      AdminConsolePostgresTest.authenticate(admin, "ADMIN"); ready.countDown();
      try {
        if (!go.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
        users.updateRoles(admin, target, Set.of(AdminUser.Role.APPROVER), 0);
        return "changed";
      } catch (AccountChangeConflictException expected) { return "conflict"; }
      finally { SecurityContextHolder.clearContext(); }
    };
    try {
      var first=pool.submit(task); var second=pool.submit(task);
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); go.countDown();
      assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder("changed", "conflict");
      assertThat(version(target)).isOne();
      assertThat(jdbc.queryForObject("SELECT count(*) FROM serms.audit_log WHERE entity_id=?", Long.class, target)).isOne();
    } finally { go.countDown(); pool.shutdownNow(); }
  }
}
