package sg.edu.nus.serms.equipment.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import sg.edu.nus.serms.equipment.domain.EquipmentSearchCriteria;
import sg.edu.nus.serms.equipment.domain.EquipmentSummary;
import sg.edu.nus.serms.testsupport.SermsFixtures;
import sg.edu.nus.serms.testsupport.SermsPostgres;

/** V005 equipment master data on real PostgreSQL: read fields and every new constraint (#40). */
class EquipmentMasterDataPostgresTest {
  private static JdbcTemplate jdbc;
  private static EquipmentSearchRepository repository;
  private static SermsFixtures fixtures;

  @BeforeAll
  static void database() {
    var dataSource = SermsPostgres.dataSource();
    jdbc = new JdbcTemplate(dataSource);
    repository = new EquipmentSearchRepository(new NamedParameterJdbcTemplate(dataSource));
    fixtures = new SermsFixtures(jdbc);
  }

  @Test
  void searchAndDetailReturnDescriptionAndMaximumDuration() {
    String name = "V005 tripod " + UUID.randomUUID();
    UUID limited = fixtures.equipment(name + " limited", "V005 Tripods", "V005 Lab", false);
    UUID plain = fixtures.equipment(name + " plain", "V005 Tripods", "V005 Lab", false);
    jdbc.update("UPDATE serms.equipment SET description = 'Carbon tripod', max_borrow_duration = 'P2DT3H'"
        + " WHERE equipment_id = ?", limited);

    var items = repository.search(new EquipmentSearchCriteria(name, null, null, null, null, false, 0, 10)).items();

    assertThat(items).extracting(EquipmentSummary::equipmentId).containsExactlyInAnyOrder(limited, plain);
    var withLimit = items.stream().filter(i -> i.equipmentId().equals(limited)).findFirst().orElseThrow();
    assertThat(withLimit.description()).isEqualTo("Carbon tripod");
    assertThat(withLimit.maxBorrowDuration()).isEqualTo(Duration.ofHours(51));
    var withoutLimit = items.stream().filter(i -> i.equipmentId().equals(plain)).findFirst().orElseThrow();
    assertThat(withoutLimit.description()).isNull();
    assertThat(withoutLimit.maxBorrowDuration()).isNull();
    assertThat(repository.findVisible(limited, null)).hasValueSatisfying(detail -> {
      assertThat(detail.description()).isEqualTo("Carbon tripod");
      assertThat(detail.maxBorrowDuration()).isEqualTo(Duration.ofHours(51));
    });
  }

  @Test
  void newColumnsDefaultForExistingWritersAndCategoriesAreRegistered() {
    String category = "V005 category " + UUID.randomUUID();
    UUID item = fixtures.equipment("V005 defaults", category, "V005 Lab", false);

    assertThat(jdbc.queryForObject("SELECT condition FROM serms.equipment WHERE equipment_id = ?",
        String.class, item)).isEqualTo("GOOD");
    assertThat(jdbc.queryForObject("SELECT serial_number IS NULL AND max_borrow_duration IS NULL"
        + " FROM serms.equipment WHERE equipment_id = ?", Boolean.class, item)).isTrue();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM serms.equipment_category WHERE name = ?",
        Long.class, category)).isOne();
  }

  @Test
  void categoryRenameCascadesAndUsedCategoryCannotBeDeleted() {
    String category = "V005 rename " + UUID.randomUUID();
    UUID item = fixtures.equipment("V005 rename", category, "V005 Lab", false);

    jdbc.update("UPDATE serms.equipment_category SET name = ? WHERE name = ?", category + " renamed", category);

    assertThat(jdbc.queryForObject("SELECT category FROM serms.equipment WHERE equipment_id = ?",
        String.class, item)).isEqualTo(category + " renamed");
    assertThatThrownBy(() -> jdbc.update("DELETE FROM serms.equipment_category WHERE name = ?", category + " renamed"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void serialNumbersAreUniqueAndOptional() {
    String serial = "SN-" + UUID.randomUUID();
    UUID first = fixtures.equipment("V005 serial one", "V005 Tripods", "V005 Lab", false);
    UUID second = fixtures.equipment("V005 serial two", "V005 Tripods", "V005 Lab", false);
    jdbc.update("UPDATE serms.equipment SET serial_number = ? WHERE equipment_id = ?", serial, first);

    assertThatThrownBy(() -> jdbc.update("UPDATE serms.equipment SET serial_number = ? WHERE equipment_id = ?",
        serial, second)).isInstanceOf(DataIntegrityViolationException.class);
    // Several items without a recorded serial number are allowed.
    UUID third = fixtures.equipment("V005 serial three", "V005 Tripods", "V005 Lab", false);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM serms.equipment WHERE equipment_id IN (?, ?)"
        + " AND serial_number IS NULL", Long.class, second, third)).isEqualTo(2);
  }

  @Test
  void invalidValuesAreRefused() {
    UUID item = fixtures.equipment("V005 invalid", "V005 Tripods", "V005 Lab", false);
    for (String assignment : new String[] {"condition = 'DAMAGED'", "max_borrow_duration = '0'",
        "max_borrow_duration = '-1 hour'", "serial_number = '  '", "description = ''"}) {
      assertThatThrownBy(() -> jdbc.update("UPDATE serms.equipment SET " + assignment + " WHERE equipment_id = ?", item))
          .as(assignment)
          .isInstanceOf(DataIntegrityViolationException.class);
    }
    assertThatThrownBy(() -> jdbc.update("INSERT INTO serms.equipment_category(name) VALUES ('   ')"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
