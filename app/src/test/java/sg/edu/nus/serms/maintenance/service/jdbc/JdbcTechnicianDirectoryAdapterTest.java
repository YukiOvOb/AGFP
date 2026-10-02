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
