package sg.edu.nus.serms.identity.service;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import sg.edu.nus.serms.identity.domain.AdminUser;
import sg.edu.nus.serms.identity.repository.AdminUserRepository;
import sg.edu.nus.serms.testsupport.SermsFixtures;
import sg.edu.nus.serms.testsupport.SermsPostgres;

class LastAdministratorPostgresTest {
  @org.springframework.context.annotation.Configuration
  @EnableTransactionManagement @EnableMethodSecurity
  static class Config {}

  @Test void concurrentSelfDemotionsCannotRemoveBothFinalAdministratorsAcrossTransactions() throws Exception {
    var ds = SermsPostgres.isolatedDataSource(); // No disabling/deleting other tests' administrators.
    try (var context = new AnnotationConfigApplicationContext()) {
      context.register(Config.class);
      context.registerBean(javax.sql.DataSource.class, () -> ds);
      context.registerBean(JdbcTemplate.class, () -> new JdbcTemplate(ds));
      context.registerBean(org.springframework.transaction.PlatformTransactionManager.class,
          () -> new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds));
      context.registerBean(org.springframework.security.crypto.password.PasswordEncoder.class,
          PasswordEncoderFactories::createDelegatingPasswordEncoder);
      context.register(AdminUserRepository.class, AdminAccess.class, AdminUserService.class);
      context.refresh();
      var jdbc = context.getBean(JdbcTemplate.class);
      var service = context.getBean(AdminUserService.class);
      var fixtures = new SermsFixtures(jdbc);
      UUID first=fixtures.user("ADMIN"), second=fixtures.user("ADMIN");
      var pool=Executors.newFixedThreadPool(2); var ready=new CountDownLatch(2); var go=new CountDownLatch(1);
      java.util.function.Function<UUID,Callable<String>> demote = id -> () -> {
        AdminConsolePostgresTest.authenticate(id, "ADMIN"); ready.countDown();
        try {
          if (!go.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
          service.updateRoles(id, id, Set.of(AdminUser.Role.BORROWER), 0);
          return "demoted";
        } catch (AccountChangeConflictException expected) {
          assertThat(expected.reason()).isEqualTo(AccountChangeConflictException.Reason.LAST_ACTIVE_ADMIN);
          return "protected";
        } finally { SecurityContextHolder.clearContext(); }
      };
      try {
        var a=pool.submit(demote.apply(first)); var b=pool.submit(demote.apply(second));
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); go.countDown();
        assertThat(List.of(a.get(20, TimeUnit.SECONDS),b.get(20, TimeUnit.SECONDS)))
            .containsExactlyInAnyOrder("demoted", "protected");
        assertThat(context.getBean(AdminUserRepository.class).activeAdministratorCount()).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM serms.audit_log WHERE action='USER_ROLES_CHANGED'", Long.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT sum(session_version) FROM serms.app_user", Long.class)).isOne();
      } finally { go.countDown(); pool.shutdownNow(); }
    }
  }
}
