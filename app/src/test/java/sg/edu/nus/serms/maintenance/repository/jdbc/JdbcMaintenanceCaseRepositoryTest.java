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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;
import sg.edu.nus.serms.maintenance.domain.*;
import sg.edu.nus.serms.maintenance.repository.MaintenanceVersionConflictException;

class JdbcMaintenanceCaseRepositoryTest {
  private static final UUID CASE = new UUID(0, 1), EQUIPMENT = new UUID(0, 2),
      REPORTER = new UUID(0, 3), TECHNICIAN = new UUID(0, 4), LOAN = new UUID(0, 5);
  private static final Instant REPORTED = Instant.parse("2026-10-02T01:02:03.123456Z"),
      RESOLVED = REPORTED.plusSeconds(60);
  private final JdbcOperations jdbc = mock(JdbcOperations.class);
  private final JdbcMaintenanceCaseRepository repository = new JdbcMaintenanceCaseRepository(jdbc);
  private String sql;
  private Object[] parameters;

  private MaintenanceCase item(MaintenanceStatus status, int version, boolean loan) {
    boolean terminal = status == MaintenanceStatus.RESOLVED || status == MaintenanceStatus.UNREPAIRABLE;
    return MaintenanceCase.restore(new MaintenanceCaseSnapshot(CASE, EQUIPMENT, REPORTER,
        status == MaintenanceStatus.OPEN ? null : TECHNICIAN, loan ? LOAN : null, status,
        "Cable failure", terminal ? "Completion note" : null, REPORTED,
        terminal ? RESOLVED : null, version));
  }

  private ResultSet row(MaintenanceCase persisted) throws Exception {
    var s = persisted.snapshot();
    ResultSet row = mock(ResultSet.class);
    when(row.getObject("maintenance_case_id", UUID.class)).thenReturn(s.maintenanceCaseId());
    when(row.getObject("equipment_id", UUID.class)).thenReturn(s.equipmentId());
    when(row.getObject("reported_by", UUID.class)).thenReturn(s.reportedBy());
    when(row.getObject("assigned_to", UUID.class)).thenReturn(s.assignedTo());
    when(row.getObject("loan_id", UUID.class)).thenReturn(s.loanId());
    when(row.getString("status")).thenReturn(s.status().name());
    when(row.getString("fault_description")).thenReturn(s.faultDescription());
    when(row.getString("resolution_note")).thenReturn(s.resolutionNote());
    when(row.getTimestamp("reported_at")).thenReturn(Timestamp.from(s.reportedAt()));
    when(row.getTimestamp("resolved_at")).thenReturn(s.resolvedAt() == null ? null : Timestamp.from(s.resolvedAt()));
    when(row.getInt("version")).thenReturn(s.version());
    return row;
  }

  private void returns(MaintenanceCase... persisted) throws Exception {
    var rows = new ArrayList<ResultSet>();
    for (var item : persisted) rows.add(row(item));
    org.mockito.stubbing.Answer<List<MaintenanceCase>> answer = call -> {
      sql = call.getArgument(0);
      parameters = call.getRawArguments().length == 3 ? (Object[]) call.getRawArguments()[2] : new Object[0];
      RowMapper<MaintenanceCase> mapper = call.getArgument(1);
      var mapped = new ArrayList<MaintenanceCase>();
      for (int i = 0; i < rows.size(); i++) mapped.add(mapper.mapRow(rows.get(i), i));
      return mapped;
    };
    when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<MaintenanceCase>>any())).thenAnswer(answer);
    when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<MaintenanceCase>>any(), any(Object[].class)))
        .thenAnswer(answer);
  }

  @Test
  void openQueueUsesOnlyOpenStatusAndOldestFirstWithIdTieBreaker() throws Exception {
    var expected = item(MaintenanceStatus.OPEN, 0, false);
    returns(expected);
    assertThat(repository.findOpenCases()).extracting(MaintenanceCase::snapshot).containsExactly(expected.snapshot());
    assertThat(sql.replaceAll("\\s+", " ").strip()).endsWith(
        "FROM serms.maintenance_case WHERE status = 'OPEN' ORDER BY reported_at ASC, maintenance_case_id ASC");
    assertThat(parameters).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(value = MaintenanceStatus.class, names = {"ASSIGNED", "IN_PROGRESS", "RESOLVED", "UNREPAIRABLE"})
  void technicianQueryBindsTechnicianIncludesTerminalAndExcludesOpen(MaintenanceStatus status) throws Exception {
    var expected = item(status, 3, false);
    returns(expected);
    assertThat(repository.findByAssignedTo(TECHNICIAN)).extracting(MaintenanceCase::snapshot)
        .containsExactly(expected.snapshot());
    assertThat(sql.replaceAll("\\s+", " ").strip()).endsWith(
        "FROM serms.maintenance_case WHERE assigned_to = ? AND status IN ('ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'UNREPAIRABLE') ORDER BY reported_at DESC, maintenance_case_id ASC");
    assertThat(parameters).containsExactly(TECHNICIAN);
  }

  @ParameterizedTest
  @EnumSource(MaintenanceStatus.class)
  void equipmentQueryBindsEquipmentAndMapsEveryStateAndNullableFields(MaintenanceStatus status) throws Exception {
    var expected = item(status, 7, status == MaintenanceStatus.IN_PROGRESS);
    returns(expected);
    assertThat(repository.findByEquipmentId(EQUIPMENT)).extracting(MaintenanceCase::snapshot)
        .containsExactly(expected.snapshot());
    assertThat(sql.replaceAll("\\s+", " ").strip()).endsWith(
        "FROM serms.maintenance_case WHERE equipment_id = ? ORDER BY reported_at DESC, maintenance_case_id ASC");
    assertThat(parameters).containsExactly(EQUIPMENT);
  }

  @ParameterizedTest
  @ValueSource(strings = {"open", "assigned", "equipment"})
  void listQueriesPreserveRowOrderAndReturnFreshAggregatesInImmutableLists(String query) throws Exception {
    var status = query.equals("assigned") ? MaintenanceStatus.ASSIGNED : MaintenanceStatus.OPEN;
    var first = item(status, 0, false);
    var s = first.snapshot();
    var second = MaintenanceCase.restore(new MaintenanceCaseSnapshot(new UUID(0, 10), EQUIPMENT,
        REPORTER, s.assignedTo(), null, status, "Second fault", null, REPORTED, null, 0));
    returns(first, second);
    var results = query(query);
    assertThat(results).extracting(MaintenanceCase::snapshot).containsExactly(first.snapshot(), second.snapshot());
    assertThatThrownBy(results::clear).isInstanceOf(UnsupportedOperationException.class);
    if (status == MaintenanceStatus.OPEN) results.get(0).assign(TECHNICIAN);
    else results.get(0).start();
    var again = query(query);
    assertThat(again.get(0)).isNotSameAs(results.get(0));
    assertThat(again.get(0).snapshot()).isEqualTo(first.snapshot());
  }

  @ParameterizedTest
  @ValueSource(strings = {"open", "assigned", "equipment"})
  void listQueriesReturnImmutableEmptyListsWhenNothingMatches(String query) throws Exception {
    returns();
    var results = query(query);
    assertThat(results).isEmpty();
    assertThatThrownBy(() -> results.add(item(MaintenanceStatus.OPEN, 0, false)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private List<MaintenanceCase> query(String query) {
    return switch (query) {
      case "open" -> repository.findOpenCases();
      case "assigned" -> repository.findByAssignedTo(TECHNICIAN);
      case "equipment" -> repository.findByEquipmentId(EQUIPMENT);
      default -> throw new AssertionError(query);
    };
  }

  @Test
  void insertBindsExistingColumnsAndRestoresDatabaseVersionZero() throws Exception {
    var expected = item(MaintenanceStatus.OPEN, 0, true);
    returns(expected);
    assertThat(repository.insert(expected).snapshot()).isEqualTo(expected.snapshot());
    assertThat(parameters).containsExactly(CASE, EQUIPMENT, REPORTER, null, LOAN, "OPEN",
        "Cable failure", null, Timestamp.from(REPORTED), null);
    assertThat(sql).contains("INSERT INTO serms.maintenance_case", "RETURNING");
    String insertedColumns = sql.substring(0, sql.indexOf("VALUES"));
    assertThat(insertedColumns).doesNotContain("version", "fault_report", "diagnosis");
  }

  @ParameterizedTest
  @EnumSource(MaintenanceStatus.class)
  void readsSnapshotIncludingNullableFieldsAndTerminalStatuses(MaintenanceStatus status) throws Exception {
    var expected = item(status, 7, status != MaintenanceStatus.OPEN);
    returns(expected);
    assertThat(repository.findById(CASE).orElseThrow().snapshot()).isEqualTo(expected.snapshot());
    assertThat(parameters).containsExactly(CASE);
    assertThat(sql).doesNotContain("FOR UPDATE");
  }

  @Test
  void missingCaseReturnsEmptyOptional() {
    when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<MaintenanceCase>>any(), any(Object[].class)))
        .thenReturn(List.of());
    assertThat(repository.findById(CASE)).isEmpty();
  }

  @Test
  void updateChecksIdAndExpectedVersionAndReturnsTriggerVersion() throws Exception {
    var expected = item(MaintenanceStatus.ASSIGNED, 18, true);
    returns(expected);
    var updated = repository.update(item(MaintenanceStatus.ASSIGNED, 17, true));
    assertThat(updated.snapshot()).isEqualTo(expected.snapshot());
    assertThat(parameters).containsExactly(TECHNICIAN, LOAN, "ASSIGNED", "Cable failure",
        null, null, CASE, 17);
    assertThat(sql).contains("WHERE maintenance_case_id = ? AND version = ?", "RETURNING");
    assertThat(sql.substring(sql.indexOf("SET"), sql.indexOf("WHERE"))).doesNotContain("version");
  }

  @ParameterizedTest
  @EnumSource(value = MaintenanceStatus.class, names = {"RESOLVED", "UNREPAIRABLE"})
  void terminalUpdateMapsResolutionAndTimestamptz(MaintenanceStatus status) throws Exception {
    returns(item(status, 4, false));
    var updated = repository.update(item(status, 3, false));
    assertThat(updated.version()).isEqualTo(4);
    assertThat(updated.resolvedAt()).isEqualTo(RESOLVED);
    assertThat(parameters).containsExactly(TECHNICIAN, null, status.name(), "Cable failure",
        "Completion note", Timestamp.from(RESOLVED), CASE, 3);
  }

  @Test
  void zeroRowUpdateRaisesTypedConflictWithExpectedVersion() {
    when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<MaintenanceCase>>any(), any(Object[].class)))
        .thenReturn(List.of());
    assertThatThrownBy(() -> repository.update(item(MaintenanceStatus.ASSIGNED, 8, false)))
        .isInstanceOf(MaintenanceVersionConflictException.class)
        .satisfies(error -> {
          var conflict = (MaintenanceVersionConflictException) error;
          assertThat(conflict.getMaintenanceCaseId()).isEqualTo(CASE);
          assertThat(conflict.getExpectedVersion()).isEqualTo(8);
        });
  }

  @Test
  void missingInsertResultIsRejected() {
    when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<MaintenanceCase>>any(), any(Object[].class)))
        .thenReturn(List.of());
    assertThatThrownBy(() -> repository.insert(item(MaintenanceStatus.OPEN, 0, false)))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("insert returned no row");
  }
}
