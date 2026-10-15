package sg.edu.nus.serms.maintenance.service.jdbc;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import sg.edu.nus.serms.maintenance.service.MaintenanceEquipmentLabel;

class JdbcMaintenanceNameDirectoryTest {
  private static final UUID CAMERA = new UUID(0, 1), TRIPOD = new UUID(0, 2), MISSING = new UUID(0, 9);
  private static final UUID ADA = new UUID(1, 1), BEN = new UUID(1, 2);
  private JdbcTemplate sql;
  private JdbcMaintenanceNameDirectory directory;

  @BeforeEach
  void fixture() {
    // Execute the SELECTs against a minimal SQL fixture, not the PostgreSQL runtime or migrations.
    sql = new JdbcTemplate(new DriverManagerDataSource(
        "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""));
    sql.execute("CREATE SCHEMA serms");
    sql.execute("CREATE TABLE serms.equipment (equipment_id UUID PRIMARY KEY, name VARCHAR NOT NULL, asset_tag VARCHAR NOT NULL)");
    sql.execute("CREATE TABLE serms.app_user (user_id UUID PRIMARY KEY, display_name VARCHAR NOT NULL)");
    sql.update("INSERT INTO serms.equipment VALUES (?, 'Field Camera', 'CAM-001'), (?, 'Tripod', 'TRI-002')",
        CAMERA, TRIPOD);
    sql.update("INSERT INTO serms.app_user VALUES (?, 'Ada Lim'), (?, 'Ben Tan')", ADA, BEN);
    directory = new JdbcMaintenanceNameDirectory(new NamedParameterJdbcTemplate(sql));
  }

  @AfterEach
  void drop() { sql.execute("DROP ALL OBJECTS"); }

  @Test
  void returnsOnlyRequestedLabelsAndOmitsUnknownIdentifiers() {
    assertThat(directory.findEquipmentLabels(Set.of(CAMERA, MISSING)))
        .containsExactlyEntriesOf(Map.of(CAMERA, new MaintenanceEquipmentLabel("Field Camera", "CAM-001")));
    assertThat(directory.findUserDisplayNames(Set.of(ADA, BEN, MISSING)))
        .containsExactlyInAnyOrderEntriesOf(Map.of(ADA, "Ada Lim", BEN, "Ben Tan"));
  }

  @Test
  void resultsAreImmutable() {
    var labels = directory.findEquipmentLabels(Set.of(CAMERA));
    var names = directory.findUserDisplayNames(Set.of(ADA));
    assertThatThrownBy(labels::clear).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(names::clear).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void emptyRequestsDoNotQueryTheDatabase() {
    var jdbc = mock(NamedParameterJdbcOperations.class);
    var adapter = new JdbcMaintenanceNameDirectory(jdbc);
    assertThat(adapter.findEquipmentLabels(Set.of())).isEmpty();
    assertThat(adapter.findUserDisplayNames(Set.of())).isEmpty();
    verifyNoInteractions(jdbc);
  }
}
