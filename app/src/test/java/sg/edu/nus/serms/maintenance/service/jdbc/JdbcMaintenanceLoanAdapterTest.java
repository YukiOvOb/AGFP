package sg.edu.nus.serms.maintenance.service.jdbc;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;
import sg.edu.nus.serms.maintenance.service.InvalidMaintenanceLoanException;

class JdbcMaintenanceLoanAdapterTest {
  private static final UUID LOAN = new UUID(0, 1), EQUIPMENT = new UUID(0, 2), OTHER = new UUID(0, 3);
  private final JdbcOperations jdbc = mock(JdbcOperations.class);
  private final JdbcMaintenanceLoanAdapter adapter = new JdbcMaintenanceLoanAdapter(jdbc);

  @Test
  void sameEquipmentLoanAcceptedWithoutActiveOrReturnedStatusRestriction() throws Exception {
    // A JDBC mapping/SQL test only; real PostgreSQL executes the lock in PostgresIT.
    var row = mock(ResultSet.class);
    when(row.getObject("equipment_id", UUID.class)).thenReturn(EQUIPMENT);
    when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<UUID>>any(), eq(LOAN)))
        .thenAnswer(call -> {
          String sql = call.getArgument(0);
          assertThat(sql.replaceAll("\\s+", " ").strip()).isEqualTo(
              "SELECT r.equipment_id FROM serms.loan l JOIN serms.reservation r ON r.reservation_id = l.reservation_id WHERE l.loan_id = ? FOR SHARE OF l");
          assertThat(sql).doesNotContain("status", "ACTIVE", "RETURNED");
          RowMapper<UUID> mapper = call.getArgument(1);
          return List.of(mapper.mapRow(row, 0));
        });
    adapter.ensureLoanMatchesEquipment(LOAN, EQUIPMENT);
    verify(row).getObject("equipment_id", UUID.class);
    verify(jdbc).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<UUID>>any(), eq(LOAN));
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing", "different-equipment"})
  void invalidAssociationIsTyped(String condition) {
    when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<UUID>>any(), eq(LOAN)))
        .thenReturn(condition.equals("missing") ? List.of() : List.of(OTHER));
    assertThatThrownBy(() -> adapter.ensureLoanMatchesEquipment(LOAN, EQUIPMENT))
        .isInstanceOf(InvalidMaintenanceLoanException.class)
        .satisfies(error -> {
          var invalid = (InvalidMaintenanceLoanException) error;
          assertThat(invalid.getLoanId()).isEqualTo(LOAN);
          assertThat(invalid.getEquipmentId()).isEqualTo(EQUIPMENT);
          assertThat(invalid.getMessage()).doesNotContain(LOAN.toString(), EQUIPMENT.toString(), "SQL");
        });
  }

  @Test
  void databaseFailurePropagatesUnchanged() {
    var unavailable = new org.springframework.dao.DataAccessResourceFailureException("Unavailable");
    when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<UUID>>any(), eq(LOAN)))
        .thenThrow(unavailable);
    assertThatThrownBy(() -> adapter.ensureLoanMatchesEquipment(LOAN, EQUIPMENT)).isSameAs(unavailable);
  }
}
