package sg.edu.nus.serms.identity.repository;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Transitional SQL mapping smoke test, not PostgreSQL migration acceptance. */
class UserAccountRepositoryTest {
  @Test
  void loadsUuidIdentityAndAllRolesWithoutExposingHashInToString() {
    var jdbc =
        new JdbcTemplate(
            new DriverManagerDataSource(
                "jdbc:h2:mem:identity_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""));
    jdbc.execute(
        "CREATE TABLE public.app_user(user_id UUID PRIMARY KEY,email VARCHAR(254),display_name VARCHAR(200),password_hash VARCHAR(200),account_status VARCHAR(20),notification_principal VARCHAR(100))");
    jdbc.execute("CREATE TABLE public.role(role_id UUID PRIMARY KEY,code VARCHAR(50))");
    jdbc.execute("CREATE TABLE public.user_role(user_id UUID,role_id UUID)");
    var user = UUID.randomUUID();
    var role1 = UUID.randomUUID();
    var role2 = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO public.app_user VALUES(?,?,?,?,?,?)",
        user,
        "wang@example.com",
        "Wang",
        "private-hash",
        "ACTIVE",
        user.toString());
    jdbc.update("INSERT INTO public.role VALUES(?,?)", role1, "BORROWER");
    jdbc.update("INSERT INTO public.role VALUES(?,?)", role2, "APPROVER");
    jdbc.update("INSERT INTO public.user_role VALUES(?,?)", user, role1);
    jdbc.update("INSERT INTO public.user_role VALUES(?,?)", user, role2);
    var repository = new UserAccountRepository(jdbc);
    var account = repository.findByEmail("wang@example.com").orElseThrow();
    assertThat(account.userId()).isEqualTo(user);
    assertThat(account.roles()).containsExactlyInAnyOrder("BORROWER", "APPROVER");
    assertThat(account.toString()).doesNotContain("private-hash");
    assertThat(repository.findById(user)).contains(account);
    assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    jdbc.execute("SHUTDOWN");
  }
}
