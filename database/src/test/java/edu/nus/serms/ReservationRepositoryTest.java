package edu.nus.serms;

import edu.nus.serms.domain.Reservation;
import edu.nus.serms.repository.ReservationRepository;
import org.junit.jupiter.api.*;
import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class ReservationRepositoryTest {
    static DataSource source;
    ReservationRepository repository;
    UUID user, otherUser, equipment;
    Instant start = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    Instant end = start.plus(1, ChronoUnit.HOURS);

    @BeforeAll static void database() throws Exception {
        String url = System.getenv("SERMS_TEST_JDBC_URL");
        assertNotNull(url, "Set SERMS_TEST_JDBC_URL to a migrated disposable PostgreSQL database named serms_test");
        source = new DataSource() {
            public Connection getConnection() throws SQLException {
                return DriverManager.getConnection(url, System.getenv("SERMS_TEST_DB_USER"), System.getenv("SERMS_TEST_DB_PASSWORD"));
            }
            public Connection getConnection(String u, String p) throws SQLException { return DriverManager.getConnection(url, u, p); }
            public PrintWriter getLogWriter() { return null; }
            public void setLogWriter(PrintWriter p) { }
            public void setLoginTimeout(int n) { }
            public int getLoginTimeout() { return 0; }
            public Logger getParentLogger() { return Logger.getGlobal(); }
            public <T> T unwrap(Class<T> c) throws SQLException { throw new SQLException("Not a wrapper"); }
            public boolean isWrapperFor(Class<?> c) { return false; }
        };
        try (Connection c = source.getConnection(); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT current_database(), version FROM serms.schema_version")) {
            assertTrue(r.next());
            assertEquals("serms_test", r.getString(1), "Never run fixtures against a development or production database");
            assertEquals(1, r.getInt(2));
        }
    }

    @BeforeEach void fixtures() throws Exception {
        repository = new ReservationRepository(source);
        user = UUID.randomUUID(); otherUser = UUID.randomUUID(); equipment = UUID.randomUUID();
        for (UUID id : new UUID[]{user, otherUser}) {
            execute("INSERT INTO serms.app_user(id,email,display_name,password_hash,role) VALUES (?,?,?,'TEST_ONLY_NOT_A_LOGIN_HASH','BORROWER')",
                id, id + "@example.invalid", "Test borrower");
        }
        execute("INSERT INTO serms.equipment(id,asset_tag,name,category,location) VALUES (?,?,'Camera 100%_kit','Camera','Lab')",
            equipment, equipment.toString());
    }

    static int execute(String sql, Object... args) throws SQLException {
        try (Connection c = source.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
            return s.executeUpdate();
        }
    }
    void state(String expected, org.junit.jupiter.api.function.Executable operation) {
        assertEquals(expected, assertThrows(SQLException.class, operation).getSQLState());
    }
    boolean available() throws SQLException {
        return repository.findAvailable(equipment.toString(), start, end).stream().anyMatch(e -> e.id().equals(equipment));
    }

    @Test void searchBookAndCancel() throws Exception {
        assertTrue(available());
        Reservation r = repository.book(user, equipment, start, end);
        assertEquals(Reservation.Status.CONFIRMED, r.status());
        assertFalse(available());
        assertFalse(repository.cancel(otherUser, r.id()));
        assertFalse(available());
        assertTrue(repository.cancel(user, r.id()));
        assertFalse(repository.cancel(user, r.id()));
        assertTrue(available());
        repository.book(otherUser, equipment, start, end);
    }

    @Test void overlappingAndContainedWindowsFailButAdjacentSucceeds() throws Exception {
        repository.book(user, equipment, start, end);
        state("23P01", () -> repository.book(otherUser, equipment, start, end));
        state("23P01", () -> repository.book(user, equipment, start.plusSeconds(1), end.minusSeconds(1)));
        state("23P01", () -> repository.book(user, equipment, start.minusSeconds(1), end.plusSeconds(1)));
        state("23P01", () -> repository.book(user, equipment, start.minusSeconds(30), start.plusSeconds(30)));
        state("23P01", () -> repository.book(user, equipment, end.minusSeconds(30), end.plusSeconds(30)));
        repository.book(user, equipment, end, end.plusSeconds(3600));
        repository.book(user, equipment, start.minusSeconds(3600), start);
    }

    @Test void pendingApprovalAlsoReservesCapacity() throws Exception {
        execute("UPDATE serms.equipment SET requires_approval=true WHERE id=?", equipment);
        Reservation r = repository.book(user, equipment, start, end);
        assertEquals(Reservation.Status.PENDING, r.status());
        assertFalse(available());
        state("23P01", () -> repository.book(otherUser, equipment, start, end));
        execute("UPDATE serms.reservation SET status='REJECTED' WHERE id=?", r.id());
        assertTrue(available());
    }

    @Test void differentEquipmentCanUseSameTime() throws Exception {
        repository.book(user, equipment, start, end);
        UUID second = UUID.randomUUID();
        execute("INSERT INTO serms.equipment(id,asset_tag,name,category,location) VALUES (?,?,'Tripod','Camera','Lab')",
            second, second.toString());
        repository.book(otherUser, second, start, end);
    }

    @Test void unavailableEquipmentCannotBeBooked() throws Exception {
        for (String status : new String[]{"MAINTENANCE", "RETIRED", "ON_LOAN"}) {
            execute("UPDATE serms.equipment SET status=? WHERE id=?", status, equipment);
            assertFalse(available());
            state("23514", () -> repository.book(user, equipment, start, end));
        }
    }

    @Test void staleAvailabilityDoesNotPermitBooking() throws Exception {
        assertTrue(available());
        execute("UPDATE serms.equipment SET status='MAINTENANCE' WHERE id=?", equipment);
        state("23514", () -> repository.book(user, equipment, start, end));
    }

    @Test void inactiveAndMissingUsersAreRejected() throws Exception {
        execute("UPDATE serms.app_user SET active=false WHERE id=?", user);
        state("23514", () -> repository.book(user, equipment, start, end));
        state("23503", () -> repository.book(UUID.randomUUID(), equipment, start, end));
        state("23503", () -> repository.book(otherUser, UUID.randomUUID(), start, end));
    }

    @Test void invalidTimeAndPastTimeAreRejected() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> repository.book(user, equipment, end, start));
        assertThrows(IllegalArgumentException.class, () -> repository.findAvailable("", start, start));
        assertThrows(IllegalArgumentException.class, () -> repository.book(user, equipment, start.plusNanos(1), end));
        Instant past = Instant.now().minusSeconds(3600).truncatedTo(ChronoUnit.SECONDS);
        state("23514", () -> repository.book(user, equipment, past, end));
        assertTrue(repository.findAvailable("", past, end).isEmpty());
    }

    @Test void searchIsCaseInsensitiveAndEscapesWildcardsAndSql() throws Exception {
        assertTrue(repository.findAvailable("cAmErA 100%_kit", start, end).stream().anyMatch(e -> e.id().equals(equipment)));
        assertTrue(repository.findAvailable("' OR 1=1 --", start, end).isEmpty());
        assertTrue(repository.findAvailable("Camera%kit", start, end).isEmpty());
    }

    @Test void databaseRejectsBypassAndInvalidTransitions() throws Exception {
        execute("UPDATE serms.equipment SET requires_approval=true WHERE id=?", equipment);
        state("23514", () -> execute(
            "INSERT INTO serms.reservation(id,user_id,equipment_id,starts_at,ends_at,status) VALUES (?,?,?,?,?,'CONFIRMED')",
            UUID.randomUUID(), user, equipment, Timestamp.from(start), Timestamp.from(end)));
        Reservation r = repository.book(user, equipment, start, end);
        state("23514", () -> execute("UPDATE serms.reservation SET status='FULFILLED' WHERE id=?", r.id()));
        state("23514", () -> execute("UPDATE serms.reservation SET ends_at=ends_at + interval '1 hour' WHERE id=?", r.id()));
        execute("UPDATE serms.reservation SET status='CONFIRMED' WHERE id=?", r.id());
        assertTrue(repository.cancel(user, r.id()));
        state("23514", () -> execute("UPDATE serms.reservation SET status='CONFIRMED' WHERE id=?", r.id()));
    }

    @Test void databaseRejectsEmptyRangesAndDirectOverlaps() throws Exception {
        state("23514", () -> execute(
            "INSERT INTO serms.reservation(id,user_id,equipment_id,starts_at,ends_at,status) VALUES (?,?,?,?,?,'CONFIRMED')",
            UUID.randomUUID(), user, equipment, Timestamp.from(start), Timestamp.from(start)));
        repository.book(user, equipment, start, end);
        state("23P01", () -> execute(
            "INSERT INTO serms.reservation(id,user_id,equipment_id,starts_at,ends_at,status) VALUES (?,?,?,?,?,'CONFIRMED')",
            UUID.randomUUID(), user, equipment, Timestamp.from(start), Timestamp.from(end)));
    }

    @Test void referentialAndIdentityConstraintsProtectHistory() throws Exception {
        repository.book(user, equipment, start, end);
        state("23503", () -> execute("DELETE FROM serms.app_user WHERE id=?", user));
        state("23503", () -> execute("DELETE FROM serms.equipment WHERE id=?", equipment));
        state("23505", () -> execute(
            "INSERT INTO serms.equipment(id,asset_tag,name,category,location) VALUES (?,?,'Other','Camera','Lab')",
            UUID.randomUUID(), equipment.toString()));
        state("23514", () -> execute("UPDATE serms.app_user SET role='SUPERUSER' WHERE id=?", user));
        state("23505", () -> execute("UPDATE serms.app_user SET email=? WHERE id=?", user + "@example.invalid", otherUser));
    }

    @Test void competingConnectionsCommitExactlyOneReservation() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2), go = new CountDownLatch(1);
        Callable<String> task = () -> {
            ready.countDown();
            if (!go.await(10, TimeUnit.SECONDS)) throw new AssertionError("Start gate timed out");
            try { repository.book(user, equipment, start, end); return "OK"; }
            catch (SQLException e) { return e.getSQLState(); }
        };
        try {
            Future<String> first = workers.submit(task), second = workers.submit(task);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            var results = java.util.List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter("OK"::equals).count());
            assertEquals(1, results.stream().filter("23P01"::equals).count());
            try (Connection c = source.getConnection(); PreparedStatement s = c.prepareStatement(
                    "SELECT count(*) FROM serms.reservation WHERE equipment_id=?")) {
                s.setObject(1, equipment);
                try (ResultSet r = s.executeQuery()) { assertTrue(r.next()); assertEquals(1, r.getInt(1)); }
            }
        } finally { go.countDown(); workers.shutdownNow(); }
    }

    @Test void equipmentStateChangeAndBookingSerialize() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (Connection c = source.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement s = c.prepareStatement("UPDATE serms.equipment SET status='MAINTENANCE' WHERE id=?")) {
                s.setObject(1, equipment); s.executeUpdate();
            }
            CountDownLatch started = new CountDownLatch(1);
            Future<String> attempt = worker.submit(() -> {
                started.countDown();
                try { repository.book(user, equipment, start, end); return "OK"; }
                catch (SQLException e) { return e.getSQLState(); }
            });
            assertTrue(started.await(10, TimeUnit.SECONDS));
            c.commit();
            assertEquals("23514", attempt.get(15, TimeUnit.SECONDS));
        } finally { worker.shutdownNow(); }
    }
}