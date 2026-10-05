package sg.edu.nus.serms.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * One throw-away PostgreSQL 18 per test JVM, migrated with the repository's own V001 and V002
 * scripts ({@code database/src/main/resources/db/migration}). Tests create their own rows with
 * random ids and never truncate, so they can share the database and run in any order.
 *
 * <p>Needs Docker. When the shared Flyway sequence moves into {@code app/}, replace {@link
 * #migrate} with Flyway and keep the rest.
 */
public final class SermsPostgres {
  static final Path MIGRATIONS = Path.of("..", "database", "src", "main", "resources", "db", "migration");
  static final String[] SCRIPTS = {"V001__sprint1.sql", "V002__align_serms_model.sql"};

  private static PostgreSQLContainer<?> container;
  private static DataSource dataSource;

  private SermsPostgres() {}

  public static synchronized DataSource dataSource() {
    if (dataSource == null) {
      container = new PostgreSQLContainer<>("postgres:18-alpine").withDatabaseName("serms_test");
      container.start();
      var ds = new PGSimpleDataSource();
      ds.setUrl(container.getJdbcUrl());
      ds.setUser(container.getUsername());
      ds.setPassword(container.getPassword());
      migrate(ds);
      dataSource = ds;
    }
    return dataSource;
  }

  private static void migrate(DataSource ds) {
    try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
      for (String script : SCRIPTS) {
        s.execute(Files.readString(MIGRATIONS.resolve(script)));
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Run tests from app/ so ../database is reachable", e);
    } catch (SQLException e) {
      throw new IllegalStateException("Could not apply the SERMS migrations", e);
    }
  }
}
