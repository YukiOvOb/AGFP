package sg.edu.nus.serms.maintenance.service.jdbc;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcOperations;
import sg.edu.nus.serms.maintenance.service.InvalidMaintenanceTechnicianException;

class JdbcTechnicianDirectoryAdapterTest {
  private static final UUID USER = new UUID(0, 4);
  private final JdbcOperations jdbc = mock(JdbcOperations.class);
  private final JdbcTechnicianDirectoryAdapter adapter = new JdbcTechnicianDirectoryAdapter(jdbc);

  @Test
  void directorySqlFiltersAndOrdersEligibleUsers() {
    // Execute the SELECT against a minimal SQL fixture, not the PostgreSQL runtime or migrations.
    var dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(
        "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
    var sql = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
    sql.execute("CREATE SCHEMA serms");
    sql.execute("CREATE TABLE serms.app_user (user_id UUID PRIMARY KEY, email VARCHAR NOT NULL, display_name VARCHAR NOT NULL, account_status VARCHAR NOT NULL)");
    sql.execute("CREATE TABLE serms.role (role_id INT PRIMARY KEY, code VARCHAR UNIQUE)");
    sql.execute("CREATE TABLE serms.user_role (user_id UUID, role_id INT, PRIMARY KEY(user_id, role_id))");
    sql.update("INSERT INTO serms.role VALUES (1, 'MAINTAINER'), (2, 'BORROWER')");
    for (int i = 1; i <= 5; i++) {
      sql.update("INSERT INTO serms.app_user VALUES (?, ?, ?, ?)", new UUID(0, i),
          "user" + i + "@example.com", i == 3 ? "Zed" : "Ada", i == 4 ? "DISABLED" : "ACTIVE");
      sql.update("INSERT INTO serms.user_role VALUES (?, ?)", new UUID(0, i), i == 5 ? 2 : 1);
    }
    sql.update("INSERT INTO serms.user_role VALUES (?, 2)", new UUID(0, 1));
    var realAdapter = new JdbcTechnicianDirectoryAdapter(sql);
    var result = realAdapter.findActiveTechnicians();
    assertThat(result).extracting(item -> item.userId()).containsExactly(
        new UUID(0, 1), new UUID(0, 2), new UUID(0, 3));
    assertThat(result.get(0).email()).isEqualTo("user1@example.com");
    assertThat(result.get(0).displayName()).isEqualTo("Ada");
    assertThatThrownBy(() -> result.clear()).isInstanceOf(UnsupportedOperationException.class);
    for (var item : result) realAdapter.ensureTechnician(item.userId());
    assertThatThrownBy(() -> realAdapter.ensureTechnician(new UUID(0, 4)))
        .isInstanceOf(InvalidMaintenanceTechnicianException.class);
    assertThatThrownBy(() -> realAdapter.ensureTechnician(new UUID(0, 5)))
        .isInstanceOf(InvalidMaintenanceTechnicianException.class);
    sql.execute("DROP ALL OBJECTS");
  }

  @Test
  void activeMaintainerAcceptedUsingOneUuidAndRoleQuery() {
    when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(USER))).thenReturn(true);
    adapter.ensureTechnician(USER);
    verify(jdbc).queryForObject(argThat(sql -> sql.contains("serms.app_user u")
        && sql.contains("JOIN serms.user_role ur ON ur.user_id = u.user_id")
        && sql.contains("JOIN serms.role r ON r.role_id = ur.role_id")
        && sql.contains("u.user_id = ? AND u.account_status = 'ACTIVE' AND r.code = 'MAINTAINER'")),
        eq(Boolean.class), eq(USER));
    verifyNoMoreInteractions(jdbc);
  }

  @ParameterizedTest(name = "{0} is rejected when the eligibility query returns false")
  @ValueSource(strings = {"missing user", "disabled user", "non-MAINTAINER"})
  void ineligibleUserRejected(String reason) {
    // The single EXISTS query yields false for each of these database conditions.
    when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(USER))).thenReturn(false);
    assertThatThrownBy(() -> adapter.ensureTechnician(USER))
        .isInstanceOf(InvalidMaintenanceTechnicianException.class)
        .hasMessageContaining("ACTIVE MAINTAINER")
        .satisfies(error -> assertThat(((InvalidMaintenanceTechnicianException) error).getUserId()).isEqualTo(USER));
  }
}
