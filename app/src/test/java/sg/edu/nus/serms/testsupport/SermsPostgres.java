package sg.edu.nus.serms.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One throw-away PostgreSQL 18 per test JVM, migrated with every repository migration in order
 * ({@code app/src/main/resources/db/migration/V*.sql}), the same sequence Flyway applies at
 * runtime. Tests create their own rows with random ids and never truncate, so they can share the
 * database and run in any order.
 *
 * <p>Until V005 this bench applied only V001 and V002; the equipment read model now selects V005
 * columns, so the bench follows the real sequence.
 */
public final class SermsPostgres {
  static final Path MIGRATIONS = Path.of("src", "main", "resources", "db", "migration");

  private static PostgreSQLContainer container;
  private static DataSource dataSource;

  private SermsPostgres() {}

  public static synchronized DataSource dataSource() {
    if (dataSource == null) {
      String external = System.getenv("SERMS_APP_TEST_JDBC_URL");
      if (external != null && !external.isBlank()) {
        var ds = new PGSimpleDataSource();
        ds.setUrl(external);
        ds.setUser(System.getenv("SERMS_APP_TEST_DB_USER"));
        ds.setPassword(System.getenv("SERMS_APP_TEST_DB_PASSWORD"));
        try (var c = ds.getConnection(); var s = c.createStatement();
            var r = s.executeQuery("SELECT current_database()")) {
          r.next();
          if (!r.getString(1).matches("serms_test(_[a-z0-9_]+)?"))
            throw new IllegalStateException("External database must be an isolated serms_test database");
        } catch (SQLException e) { throw new IllegalStateException(e); }
        migrate(ds);
        dataSource = ds;
        return dataSource;
      }
      container = new PostgreSQLContainer("postgres:18-alpine").withDatabaseName("serms_test");
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

  /** V001, V002, ... in version order (the names are zero-padded, so name order is version order). */
  public static DataSource isolatedDataSource() {
    var original = (PGSimpleDataSource) dataSource();
    String database = "serms_test_isolated_" + java.util.UUID.randomUUID().toString().replace("-", "");
    try (var connection = original.getConnection(); var statement = connection.createStatement()) {
      // Identifier is entirely generated here, never derived from user/config input.
      statement.execute("CREATE DATABASE " + database);
    } catch (SQLException e) { throw new IllegalStateException("Could not create isolated test database", e); }
    var isolated = new PGSimpleDataSource();
    isolated.setURL(original.getURL()); isolated.setDatabaseName(database);
    isolated.setUser(original.getUser()); isolated.setPassword(original.getPassword());
    migrate(isolated);
    return isolated;
  }

  /** V001, V002, ... in version order (the names are zero-padded, so name order is version order). */
  static List<Path> scripts() throws IOException {
    try (var files = Files.list(MIGRATIONS)) {
      return files.filter(f -> f.getFileName().toString().matches("V\\d{3}__.+\\.sql")).sorted().toList();
    }
  }

  private static void migrate(DataSource ds) {
    try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
      for (Path script : scripts()) s.execute(Files.readString(script));
    } catch (IOException e) {
      throw new UncheckedIOException("Run tests from app/ so src/main/resources/db/migration is reachable", e);
    } catch (SQLException e) {
      throw new IllegalStateException("Could not apply the SERMS migrations", e);
    }
  }
}
