package sg.edu.nus.serms.maintenance.repository.jdbc;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;
import sg.edu.nus.serms.maintenance.domain.*;

class JdbcMaintenanceHistoryRepositoryTest {
  private static final UUID CASE = new UUID(0, 1), EQUIPMENT = new UUID(0, 2), ACTOR = new UUID(0, 3);
  private static final Instant NOW = Instant.parse("2026-10-02T02:03:04.123456Z");
  private final JdbcOperations jdbc = mock(JdbcOperations.class);
  private final JdbcMaintenanceHistoryRepository repository = new JdbcMaintenanceHistoryRepository(jdbc);
  private String sql;
  private Object[] parameters;

  private ResultSet row(MaintenanceHistoryAction action, Instant at, String detail) throws Exception {
    ResultSet row = mock(ResultSet.class);
    when(row.getObject("entity_id", UUID.class)).thenReturn(CASE);
    when(row.getObject("equipment_id", UUID.class)).thenReturn(EQUIPMENT);
    when(row.getString("action")).thenReturn("MAINTENANCE_" + action.name());
    when(row.getObject("actor_id", UUID.class)).thenReturn(ACTOR);
    when(row.getTimestamp("occurred_at")).thenReturn(Timestamp.from(at));
    when(row.getString("change_summary")).thenReturn(detail);
    return row;
  }

  private void returns(ResultSet... rows) {
    when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<MaintenanceHistoryEntry>>any(), any(Object[].class)))
        .thenAnswer(call -> {
          sql = call.getArgument(0);
          parameters = (Object[]) call.getRawArguments()[2];
          RowMapper<MaintenanceHistoryEntry> mapper = call.getArgument(1);
          var mapped = new ArrayList<MaintenanceHistoryEntry>();
          for (int index = 0; index < rows.length; index++) mapped.add(mapper.mapRow(rows[index], index));
          return mapped;
        });
  }

  @Test
  void appendMapsAuditFieldsAndGeneratesUniqueLocalCorrelation() {
    var writes = new ArrayList<Object[]>();
    when(jdbc.update(anyString(), any(Object[].class))).thenAnswer(call -> {
      assertThat((String) call.getArgument(0)).contains("INSERT INTO serms.audit_log",
          "'MaintenanceCase'", "'SUCCESS'", "request_id", "change_summary", "occurred_at");
      writes.add((Object[]) call.getRawArguments()[1]);
      return 1;
    });
    var entry = new MaintenanceHistoryEntry(CASE, EQUIPMENT, MaintenanceHistoryAction.ADD_NOTE,
        MaintenanceStatus.IN_PROGRESS, MaintenanceStatus.IN_PROGRESS, ACTOR, NOW, "Inspected cable");
    repository.append(entry);
    repository.append(entry);
    for (Object[] values : writes) {
      assertThat(values[0]).isInstanceOf(UUID.class);
      assertThat(values).containsExactly(values[0], ACTOR, "MAINTENANCE_ADD_NOTE", CASE,
          "maintenance:" + values[0], "Inspected cable", Timestamp.from(NOW));
    }
    assertThat(writes.get(0)[0]).isNotEqualTo(writes.get(1)[0]);
    assertThat(writes.get(0)[4]).isNotEqualTo(writes.get(1)[4]);
  }

  @Test
  void nullableDetailStoredAsNull() {
    repository.append(new MaintenanceHistoryEntry(CASE, EQUIPMENT, MaintenanceHistoryAction.ASSIGN,
        MaintenanceStatus.OPEN, MaintenanceStatus.ASSIGNED, ACTOR, NOW, null));
    verify(jdbc).update(anyString(), any(UUID.class), eq(ACTOR), eq("MAINTENANCE_ASSIGN"), eq(CASE),
        startsWith("maintenance:"), isNull(), eq(Timestamp.from(NOW)));
  }

  static Stream<Arguments> transitions() {
    return Stream.of(
        Arguments.of(MaintenanceHistoryAction.REPORT_FAULT, null, MaintenanceStatus.OPEN),
        Arguments.of(MaintenanceHistoryAction.ASSIGN, MaintenanceStatus.OPEN, MaintenanceStatus.ASSIGNED),
        Arguments.of(MaintenanceHistoryAction.START, MaintenanceStatus.ASSIGNED, MaintenanceStatus.IN_PROGRESS),
        Arguments.of(MaintenanceHistoryAction.RECORD_DIAGNOSIS, MaintenanceStatus.IN_PROGRESS, MaintenanceStatus.IN_PROGRESS),
        Arguments.of(MaintenanceHistoryAction.RECORD_REPAIR_ACTION, MaintenanceStatus.IN_PROGRESS, MaintenanceStatus.IN_PROGRESS),
        Arguments.of(MaintenanceHistoryAction.ADD_NOTE, MaintenanceStatus.IN_PROGRESS, MaintenanceStatus.IN_PROGRESS),
        Arguments.of(MaintenanceHistoryAction.RESOLVE, MaintenanceStatus.IN_PROGRESS, MaintenanceStatus.RESOLVED),
        Arguments.of(MaintenanceHistoryAction.MARK_UNREPAIRABLE, MaintenanceStatus.IN_PROGRESS, MaintenanceStatus.UNREPAIRABLE));
  }

  @ParameterizedTest
  @MethodSource("transitions")
  void reconstructsAllTransitionsAndDetail(MaintenanceHistoryAction action,
      MaintenanceStatus before, MaintenanceStatus after) throws Exception {
    returns(row(action, NOW, "Work details"));
    assertThat(repository.findByCaseId(CASE)).containsExactly(
        new MaintenanceHistoryEntry(CASE, EQUIPMENT, action, before, after, ACTOR, NOW, "Work details"));
  }

  @Test
  void caseHistoryLimitsActionsAndUsesDeterministicChronologicalOrder() throws Exception {
    returns(row(MaintenanceHistoryAction.REPORT_FAULT, NOW, "Broken cable"),
        row(MaintenanceHistoryAction.ASSIGN, NOW.plusSeconds(1), null));
    var result = repository.findByCaseId(CASE);
    assertThat(result).extracting(MaintenanceHistoryEntry::action).containsExactly(
        MaintenanceHistoryAction.REPORT_FAULT, MaintenanceHistoryAction.ASSIGN);
    assertThat(result).extracting(MaintenanceHistoryEntry::occurredAt).containsExactly(NOW, NOW.plusSeconds(1));
    assertThat(result.get(1).detail()).isNull();
    assertThat(sql).contains("a.entity_type = 'MaintenanceCase'", "a.entity_id = ?",
        "a.outcome = 'SUCCESS'", "a.action IN (", "ORDER BY a.occurred_at ASC, a.audit_id ASC");
    for (var action : MaintenanceHistoryAction.values()) assertThat(sql).contains("'MAINTENANCE_" + action.name() + "'");
    assertThat(sql).doesNotContain("'LOAN_", "'RESERVATION_", "FOR UPDATE");
    assertThat(parameters).containsExactly(CASE);
  }

  @Test
  void equipmentHistoryJoinsExistingCasesAndMapsWorkNoteDetail() throws Exception {
    returns(row(MaintenanceHistoryAction.ADD_NOTE, NOW, "Checked connector"));
    assertThat(repository.findByEquipmentId(EQUIPMENT).get(0).detail()).isEqualTo("Checked connector");
    assertThat(sql).contains("JOIN serms.maintenance_case m ON m.maintenance_case_id = a.entity_id",
        "AND m.equipment_id = ?", "ORDER BY a.occurred_at ASC, a.audit_id ASC");
    assertThat(parameters).containsExactly(EQUIPMENT);
  }

  @Test
  void emptyAuditResultReturnsEmptyHistory() {
    returns();
    assertThat(repository.findByCaseId(CASE)).isEmpty();
  }
}
