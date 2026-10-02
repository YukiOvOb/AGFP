package sg.edu.nus.serms.maintenance.service.jdbc;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;

class JdbcEquipmentMaintenanceAdapterTest {
  private static final UUID EQUIPMENT = new UUID(0, 2);
  private final JdbcOperations jdbc = mock(JdbcOperations.class);
  private final JdbcEquipmentMaintenanceAdapter adapter = new JdbcEquipmentMaintenanceAdapter(jdbc);

  private void current(String status) {
    when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<String>>any(), any(Object[].class)))
        .thenReturn(List.of(status));
  }

  private void facts(boolean activeCase, boolean activeLoan) {
    when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(EQUIPMENT))).thenAnswer(call -> {
      String sql = call.getArgument(0);
      if (sql.contains("serms.maintenance_case")) {
        assertThat(sql).contains("'OPEN', 'ASSIGNED', 'IN_PROGRESS'");
        return activeCase;
      }
      assertThat(sql).contains("JOIN serms.reservation r USING (reservation_id)",
          "r.equipment_id = ?", "l.status = 'ACTIVE'");
      return activeLoan;
    });
  }

  private void lockedBeforeUpdate(String target) {
    var order = inOrder(jdbc);
    order.verify(jdbc).query(contains("FOR UPDATE"), org.mockito.ArgumentMatchers.<RowMapper<String>>any(), eq(EQUIPMENT));
    order.verify(jdbc).update(anyString(), eq(target), eq(EQUIPMENT));
  }

  @Test
  void existenceUsesEquipmentUuidAndRejectsMissingRow() {
    when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(EQUIPMENT))).thenReturn(false);
    assertThatIllegalArgumentException().isThrownBy(() -> adapter.ensureEquipmentExists(EQUIPMENT))
        .withMessageContaining(EQUIPMENT.toString());
    verify(jdbc).queryForObject(contains("FROM serms.equipment WHERE equipment_id = ?"), eq(Boolean.class), eq(EQUIPMENT));
  }

  @Test
  void existingEquipmentAcceptedWithoutWrite() {
    when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(EQUIPMENT))).thenReturn(true);
    adapter.ensureEquipmentExists(EQUIPMENT);
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void missingEquipmentLockRejected() {
    when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<String>>any(), any(Object[].class)))
        .thenReturn(List.of());
    assertThatIllegalArgumentException().isThrownBy(() -> adapter.lockForMaintenance(EQUIPMENT))
        .withMessageContaining("does not exist");
  }

  @Test
  void lockMapsStatusAndUsesEquipmentForUpdate() throws Exception {
    var row = mock(java.sql.ResultSet.class);
    when(row.getString("status")).thenReturn("AVAILABLE");
    when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<String>>any(), any(Object[].class)))
        .thenAnswer(call -> {
          RowMapper<String> mapper = call.getArgument(1);
          return List.of(mapper.mapRow(row, 0));
        });
    adapter.lockForMaintenance(EQUIPMENT);
    verify(jdbc).query(contains("WHERE equipment_id = ? FOR UPDATE"),
        org.mockito.ArgumentMatchers.<RowMapper<String>>any(), eq(EQUIPMENT));
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  @ParameterizedTest
  @ValueSource(strings = {"AVAILABLE", "ON_LOAN"})
  void reportFaultLocksAndMarksUnderMaintenance(String status) {
    current(status);
    adapter.markUnderMaintenance(EQUIPMENT);
    lockedBeforeUpdate("UNDER_MAINTENANCE");
  }

  @Test
  void reportingRetiredEquipmentIsRejectedWithoutWrite() {
    current("RETIRED");
    assertThatIllegalArgumentException().isThrownBy(() -> adapter.markUnderMaintenance(EQUIPMENT))
        .withMessageContaining("RETIRED");
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void alreadyUnderMaintenanceAvoidsVersionBump() {
    current("UNDER_MAINTENANCE");
    adapter.markUnderMaintenance(EQUIPMENT);
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  static Stream<Arguments> recomputations() {
    return Stream.of(Arguments.of(true, true, "UNDER_MAINTENANCE"),
        Arguments.of(false, true, "ON_LOAN"), Arguments.of(false, false, "AVAILABLE"));
  }

  @ParameterizedTest
  @MethodSource("recomputations")
  void resolvedUsesDatabasePrecedence(boolean activeCase, boolean activeLoan, String target) {
    current("ON_LOAN".equals(target) ? "UNDER_MAINTENANCE" : "ON_LOAN");
    facts(activeCase, activeLoan);
    adapter.recomputeAfterResolvedMaintenance(EQUIPMENT);
    lockedBeforeUpdate(target);
    if (activeCase) verify(jdbc, never()).queryForObject(contains("serms.loan"), eq(Boolean.class), eq(EQUIPMENT));
  }

  @ParameterizedTest
  @MethodSource("recomputations")
  void sameRecomputedStateAvoidsVersionBump(boolean activeCase, boolean activeLoan, String target) {
    current(target);
    facts(activeCase, activeLoan);
    adapter.recomputeAfterResolvedMaintenance(EQUIPMENT);
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void retiredWinsAndSkipsCaseAndLoanQueries() {
    current("RETIRED");
    adapter.recomputeAfterResolvedMaintenance(EQUIPMENT);
    verify(jdbc, never()).queryForObject(anyString(), eq(Boolean.class), any(Object[].class));
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void unrepairableLocksAndRetiresEquipment() {
    current("UNDER_MAINTENANCE");
    adapter.markRetired(EQUIPMENT);
    lockedBeforeUpdate("RETIRED");
  }

  @Test
  void alreadyRetiredAvoidsVersionBump() {
    current("RETIRED");
    adapter.markRetired(EQUIPMENT);
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }
}
