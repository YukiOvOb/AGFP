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
             ResultSet r = s.executeQuery("SELECT current_database(), max(version) FROM serms.schema_version")) {
            assertTrue(r.next());
            assertEquals("serms_test", r.getString(1), "Never run fixtures against a development or production database");
            assertEquals(2, r.getInt(2));
        }
    }

    @BeforeEach void fixtures() throws Exception {
        repository = new ReservationRepository(source);
        user = UUID.randomUUID(); otherUser = UUID.randomUUID(); equipment = UUID.randomUUID();
        for (UUID id : new UUID[]{user, otherUser}) {
            execute("INSERT INTO serms.app_user(user_id,email,display_name,password_hash) VALUES (?,?,?,'TEST_ONLY_NOT_A_LOGIN_HASH')",
                id, id + "@example.invalid", "Test borrower");
        }
        execute("INSERT INTO serms.equipment(equipment_id,asset_tag,name,category,location) VALUES (?,?,'Camera 100%_kit','Camera','Lab')",
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
        return repository.findAvailable(equipment.toString(), start, end).stream().anyMatch(e -> e.equipmentId().equals(equipment));
    }

    @Test void searchBookAndCancel() throws Exception {
        assertTrue(available());
        Reservation r = repository.book(user, equipment, start, end);
        assertEquals(Reservation.Status.CONFIRMED, r.status());
        assertFalse(available());
        assertFalse(repository.cancel(otherUser, r.reservationId()));
        assertFalse(available());
        assertTrue(repository.cancel(user, r.reservationId()));
        assertFalse(repository.cancel(user, r.reservationId()));
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
        execute("UPDATE serms.equipment SET requires_approval=true WHERE equipment_id=?", equipment);
        Reservation r = repository.book(user, equipment, start, end);
        assertEquals(Reservation.Status.PENDING_APPROVAL, r.status());
        assertFalse(available());
        state("23P01", () -> repository.book(otherUser, equipment, start, end));
        execute("UPDATE serms.reservation SET status='REJECTED' WHERE reservation_id=?", r.reservationId());
        assertTrue(available());
    }

    @Test void differentEquipmentCanUseSameTime() throws Exception {
        repository.book(user, equipment, start, end);
        UUID second = UUID.randomUUID();
        execute("INSERT INTO serms.equipment(equipment_id,asset_tag,name,category,location) VALUES (?,?,'Tripod','Camera','Lab')",
            second, second.toString());
        repository.book(otherUser, second, start, end);
    }

    @Test void unavailableEquipmentCannotBeBooked() throws Exception {
        for (String status : new String[]{"UNDER_MAINTENANCE", "ON_LOAN", "RETIRED"}) {
            execute("UPDATE serms.equipment SET status=? WHERE equipment_id=?", status, equipment);
            assertFalse(available());
            state("23514", () -> repository.book(user, equipment, start, end));
        }
    }

    @Test void staleAvailabilityDoesNotPermitBooking() throws Exception {
        assertTrue(available());
        execute("UPDATE serms.equipment SET status='UNDER_MAINTENANCE' WHERE equipment_id=?", equipment);
        state("23514", () -> repository.book(user, equipment, start, end));
    }

    @Test void inactiveAndMissingUsersAreRejected() throws Exception {
        execute("UPDATE serms.app_user SET account_status='DISABLED' WHERE user_id=?", user);
        state("23514", () -> repository.book(user, equipment, start, end));
        state("23503", () -> repository.book(UUID.randomUUID(), equipment, start, end));
        state("23503", () -> repository.book(otherUser, UUID.randomUUID(), start, end));
    }

    @Test void invalidTimeAndPastTimeAreRejected() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> repository.book(user, equipment, end, start));
        assertThrows(IllegalArgumentException.class, () -> repository.findAvailable("", start, start));
        assertThrows(IllegalArgumentException.class, () -> repository.book(user, equipment, start.plusNanos(1), end));
        Instant past = Instant.now().minusSeconds(3600).truncatedTo(ChronoUnit.SECONDS);
        state("23514", () -> repository.book(user, equipment, past.minusSeconds(3600), past));
        assertTrue(repository.findAvailable("", past.minusSeconds(3600), past).isEmpty());
    }

    @Test void searchIsCaseInsensitiveAndEscapesWildcardsAndSql() throws Exception {
        assertTrue(repository.findAvailable("cAmErA 100%_kit", start, end).stream().anyMatch(e -> e.equipmentId().equals(equipment)));
        assertTrue(repository.findAvailable("' OR 1=1 --", start, end).isEmpty());
        assertTrue(repository.findAvailable("Camera%kit", start, end).isEmpty());
    }

    @Test void databaseRejectsBypassAndInvalidTransitions() throws Exception {
        execute("UPDATE serms.equipment SET requires_approval=true WHERE equipment_id=?", equipment);
        state("23514", () -> execute(
            "INSERT INTO serms.reservation(reservation_id,requester_id,equipment_id,start_at,end_at,status) VALUES (?,?,?,?,?,'CONFIRMED')",
            UUID.randomUUID(), user, equipment, Timestamp.from(start), Timestamp.from(end)));
        Reservation r = repository.book(user, equipment, start, end);
        state("23514", () -> execute("UPDATE serms.reservation SET status='FULFILLED' WHERE reservation_id=?", r.reservationId()));
        state("23514", () -> execute("UPDATE serms.reservation SET end_at=end_at + interval '1 hour' WHERE reservation_id=?", r.reservationId()));
        execute("UPDATE serms.reservation SET status='CONFIRMED' WHERE reservation_id=?", r.reservationId());
        assertTrue(repository.cancel(user, r.reservationId()));
        state("23514", () -> execute("UPDATE serms.reservation SET status='CONFIRMED' WHERE reservation_id=?", r.reservationId()));
    }

    @Test void databaseRejectsEmptyRangesAndDirectOverlaps() throws Exception {
        state("23514", () -> execute(
            "INSERT INTO serms.reservation(reservation_id,requester_id,equipment_id,start_at,end_at,status) VALUES (?,?,?,?,?,'CONFIRMED')",
            UUID.randomUUID(), user, equipment, Timestamp.from(start), Timestamp.from(start)));
        repository.book(user, equipment, start, end);
        state("23P01", () -> execute(
            "INSERT INTO serms.reservation(reservation_id,requester_id,equipment_id,start_at,end_at,status) VALUES (?,?,?,?,?,'CONFIRMED')",
            UUID.randomUUID(), user, equipment, Timestamp.from(start), Timestamp.from(end)));
    }

    @Test void referentialAndIdentityConstraintsProtectHistory() throws Exception {
        repository.book(user, equipment, start, end);
        state("23503", () -> execute("DELETE FROM serms.app_user WHERE user_id=?", user));
        state("23503", () -> execute("DELETE FROM serms.equipment WHERE equipment_id=?", equipment));
        state("23505", () -> execute(
            "INSERT INTO serms.equipment(equipment_id,asset_tag,name,category,location) VALUES (?,?,'Other','Camera','Lab')",
            UUID.randomUUID(), equipment.toString()));
        state("23514", () -> execute("INSERT INTO serms.role(role_id,code,name) VALUES (?,'SUPERUSER','Invalid')", UUID.randomUUID()));
        state("23505", () -> execute("UPDATE serms.app_user SET email=? WHERE user_id=?", user + "@example.invalid", otherUser));
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
            try (PreparedStatement s = c.prepareStatement("UPDATE serms.equipment SET status='UNDER_MAINTENANCE' WHERE equipment_id=?")) {
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

    static String scalar(String sql, Object... args) throws SQLException {
        try (Connection c = source.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            for (int i=0; i<args.length; i++) s.setObject(i+1,args[i]);
            try (ResultSet r=s.executeQuery()) { assertTrue(r.next()); return r.getString(1); }
        }
    }

    UUID activeLoan(Instant due) throws Exception {
        Instant checkedOut=Instant.now().minusSeconds(30).truncatedTo(ChronoUnit.SECONDS);
        Reservation reservation=repository.book(user,equipment,checkedOut.minusSeconds(30),due);
        UUID loan=UUID.randomUUID();
        try (Connection c=source.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement s=c.prepareStatement("""
                    INSERT INTO serms.loan(loan_id,reservation_id,checkout_by,checked_out_at,due_at)
                    VALUES (?,?,?,?,?)
                    """)) {
                s.setObject(1,loan); s.setObject(2,reservation.reservationId()); s.setObject(3,otherUser);
                s.setTimestamp(4,Timestamp.from(checkedOut)); s.setTimestamp(5,Timestamp.from(due)); s.executeUpdate();
            }
            try (PreparedStatement s=c.prepareStatement("UPDATE serms.equipment SET status='ON_LOAN' WHERE equipment_id=?")) {
                s.setObject(1,equipment); s.executeUpdate();
            }
            try (PreparedStatement s=c.prepareStatement("UPDATE serms.reservation SET status='FULFILLED' WHERE reservation_id=?")) {
                s.setObject(1,reservation.reservationId()); s.executeUpdate();
            }
            c.commit();
        }
        return loan;
    }

    @Test void v1UpgradePreservesIdentifiersRolesAndBusinessData() throws Exception {
        UUID oldUser=UUID.fromString("10000000-0000-0000-0000-000000000001");
        assertEquals("DISABLED",scalar("SELECT account_status FROM serms.app_user WHERE user_id=?",oldUser));
        assertEquals("MAINTAINER",scalar("""
            SELECT r.code FROM serms.user_role ur JOIN serms.role r USING(role_id) WHERE ur.user_id=?
            """,oldUser));
        assertEquals("UNDER_MAINTENANCE",scalar("SELECT status FROM serms.equipment WHERE asset_tag='V1-UPGRADE'"));
        assertEquals("PENDING_APPROVAL",scalar("""
            SELECT status FROM serms.reservation WHERE reservation_id='10000000-0000-0000-0000-000000000003'
            """));
        assertEquals("legacy hash retained",scalar("SELECT password_hash FROM serms.app_user WHERE user_id=?",oldUser));
        assertEquals("5",scalar("SELECT count(*) FROM serms.role"));
    }

    @Test void userHasMultipleRolesWithoutDuplicateGrants() throws Exception {
        execute("INSERT INTO serms.user_role SELECT ?,role_id FROM serms.role WHERE code IN ('BORROWER','CUSTODIAN')",user);
        assertEquals("2",scalar("SELECT count(*) FROM serms.user_role WHERE user_id=?",user));
        state("23505",()->execute("INSERT INTO serms.user_role SELECT ?,role_id FROM serms.role WHERE code='BORROWER'",user));
        state("23503",()->execute("INSERT INTO serms.user_role VALUES (?,?)",UUID.randomUUID(),UUID.randomUUID()));
    }

    @Test void fulfilledReservationKeepsOriginalSlotAfterEarlyReturn() throws Exception {
        UUID loan=activeLoan(end);
        execute("""
            UPDATE serms.loan SET status='RETURNED',return_by=?,returned_at=statement_timestamp(),return_condition='GOOD'
            WHERE loan_id=?
            """,otherUser,loan);
        execute("UPDATE serms.equipment SET status='AVAILABLE' WHERE equipment_id=?",equipment);
        assertFalse(available());
        state("23P01",()->repository.book(user,equipment,start,end));
        repository.book(user,equipment,end,end.plusSeconds(3600));
    }

    @Test void onLoanAllowsOnlyNonOverdueFutureSlotAfterDue() throws Exception {
        activeLoan(end);
        assertFalse(available());
        assertTrue(repository.findAvailable(equipment.toString(),end,end.plusSeconds(3600)).stream()
            .anyMatch(e->e.equipmentId().equals(equipment)));
        repository.book(user,equipment,end,end.plusSeconds(3600));
    }

    @Test void overdueLoanBlocksFutureReservationsAndLateDamageRemainsIndependent() throws Exception {
        Instant due=Instant.now().plusSeconds(2).truncatedTo(ChronoUnit.MICROS);
        UUID loan=activeLoan(due);
        long wait=java.time.Duration.between(Instant.now(),due.plusMillis(50)).toMillis();
        if (wait>0) Thread.sleep(wait); // Cross a real due-time boundary; overdue is never a stored flag.
        assertFalse(available());
        state("23514",()->repository.book(user,equipment,start,end));
        execute("""
            UPDATE serms.loan SET status='RETURNED',return_by=?,returned_at=statement_timestamp(),
                return_condition='DAMAGED',return_note='Broken lens' WHERE loan_id=?
            """,otherUser,loan);
        assertEquals("t",scalar("SELECT returned_at>due_at AND return_condition='DAMAGED' FROM serms.loan WHERE loan_id=?",loan));
        state("23514",()->execute("UPDATE serms.loan SET return_note='Overwrite' WHERE loan_id=?",loan));
    }

    @Test void loanUniquenessReturnFieldsAndDueTimeAreGuarded() throws Exception {
        UUID loan=activeLoan(end);
        UUID reservation=UUID.fromString(scalar("SELECT reservation_id FROM serms.loan WHERE loan_id=?",loan));
        state("23514",()->execute("UPDATE serms.loan SET status='RETURNED' WHERE loan_id=?",loan));
        state("23514",()->execute("UPDATE serms.loan SET due_at=due_at+interval '1 hour' WHERE loan_id=?",loan));
        state("23514",()->execute("""
            UPDATE serms.loan SET status='RETURNED',return_by=?,returned_at=statement_timestamp(),
                return_condition='DAMAGED' WHERE loan_id=?
            """,otherUser,loan));
        state("23514",()->execute("""
            INSERT INTO serms.loan(loan_id,reservation_id,checkout_by,checked_out_at,due_at)
            SELECT ?,reservation_id,checkout_by,checked_out_at,due_at FROM serms.loan WHERE loan_id=?
            """,UUID.randomUUID(),loan));
        execute("""
            UPDATE serms.loan SET status='RETURNED',return_by=?,returned_at=statement_timestamp(),return_condition='GOOD'
            WHERE loan_id=?
            """,otherUser,loan);
        state("23505",()->execute("""
            INSERT INTO serms.loan(loan_id,reservation_id,checkout_by,checked_out_at,due_at)
            SELECT ?,reservation_id,checkout_by,checked_out_at,due_at FROM serms.loan WHERE loan_id=?
            """,UUID.randomUUID(),loan));
        assertNotNull(reservation);
    }

    @Test void maintenanceMustMatchLoanAndBlocksBookingEvenWithStaleEquipmentStatus() throws Exception {
        UUID loan=activeLoan(end), second=UUID.randomUUID(), caseId=UUID.randomUUID();
        execute("INSERT INTO serms.equipment(equipment_id,asset_tag,name,category,location) VALUES (?,?,'Other','Camera','Lab')",
            second,second.toString());
        state("23514",()->execute("""
            INSERT INTO serms.maintenance_case(maintenance_case_id,equipment_id,reported_by,loan_id,fault_description)
            VALUES (?,?,?,?,'Damage')
            """,caseId,second,user,loan));
        execute("""
            INSERT INTO serms.maintenance_case(maintenance_case_id,equipment_id,reported_by,loan_id,fault_description)
            VALUES (?,?,?,?,'Damage')
            """,caseId,equipment,user,loan);
        assertTrue(repository.findAvailable(equipment.toString(),end,end.plusSeconds(3600)).isEmpty());
        state("23514",()->repository.book(user,equipment,end,end.plusSeconds(3600)));
        state("23514",()->execute("UPDATE serms.maintenance_case SET status='RESOLVED' WHERE maintenance_case_id=?",caseId));
        state("23514",()->execute("UPDATE serms.maintenance_case SET status='ASSIGNED' WHERE maintenance_case_id=?",caseId));
        execute("UPDATE serms.maintenance_case SET status='ASSIGNED',assigned_to=? WHERE maintenance_case_id=?",otherUser,caseId);
        execute("UPDATE serms.maintenance_case SET status='IN_PROGRESS' WHERE maintenance_case_id=?",caseId);
        execute("""
            UPDATE serms.maintenance_case SET status='RESOLVED',resolution_note='Repaired',resolved_at=statement_timestamp()
            WHERE maintenance_case_id=?
            """,caseId);
        assertEquals("ACTIVE",scalar("SELECT status FROM serms.loan WHERE loan_id=?",loan));
        state("23514",()->execute("UPDATE serms.maintenance_case SET status='OPEN',resolved_at=NULL WHERE maintenance_case_id=?",caseId));
        assertFalse(repository.findAvailable(equipment.toString(),end,end.plusSeconds(3600)).isEmpty());
    }

    @Test void approvalRejectsSelfApprovalMissingReasonAndDuplicateOrChangedDecision() throws Exception {
        execute("UPDATE serms.equipment SET requires_approval=true WHERE equipment_id=?",equipment);
        Reservation r=repository.book(user,equipment,start,end);
        String sql="INSERT INTO serms.approval_decision(approval_id,reservation_id,approver_id,decision,comment) VALUES (?,?,?,'REJECT',?)";
        state("23514",()->execute(sql,UUID.randomUUID(),r.reservationId(),user,"Self"));
        state("23514",()->execute(sql,UUID.randomUUID(),r.reservationId(),otherUser," "));
        UUID decision=UUID.randomUUID();
        execute(sql,decision,r.reservationId(),otherUser,"Not suitable");
        state("23505",()->execute(sql,UUID.randomUUID(),r.reservationId(),otherUser,"Duplicate"));
        state("23514",()->execute("UPDATE serms.approval_decision SET comment='Overwrite' WHERE approval_id=?",decision));
    }

    @Test void notificationRequiresExactlyOneTargetAndConsistentDeliveryAndDeduplication() throws Exception {
        Reservation r=repository.book(user,equipment,start,end);
        UUID notification=UUID.randomUUID();
        state("23514",()->execute("""
            INSERT INTO serms.notification(notification_id,recipient_id,type,content,dedup_key)
            VALUES (?,?,'RESERVATION','Message',?)
            """,notification,user,notification.toString()));
        execute("""
            INSERT INTO serms.notification(notification_id,recipient_id,reservation_id,type,content,dedup_key)
            VALUES (?,?,?,'RESERVATION','Message',?)
            """,notification,user,r.reservationId(),notification.toString());
        state("23505",()->execute("""
            INSERT INTO serms.notification(notification_id,recipient_id,reservation_id,type,content,dedup_key)
            VALUES (?,?,?,'RESERVATION','Duplicate',?)
            """,UUID.randomUUID(),user,r.reservationId(),notification.toString()));
        UUID loan=activeLoan(Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.SECONDS));
        state("23514",()->execute("UPDATE serms.notification SET loan_id=? WHERE notification_id=?",loan,notification));
        state("23514",()->execute("UPDATE serms.notification SET read_at=statement_timestamp() WHERE notification_id=?",notification));
        state("23514",()->execute("UPDATE serms.notification SET delivery_status='SENT' WHERE notification_id=?",notification));
        execute("UPDATE serms.notification SET delivery_status='FAILED',attempt_count=1 WHERE notification_id=?",notification);
        execute("""
            UPDATE serms.notification SET delivery_status='SENT',attempt_count=2,delivered_at=statement_timestamp()
            WHERE notification_id=?
            """,notification);
        execute("UPDATE serms.notification SET read_at=statement_timestamp() WHERE notification_id=?",notification);
        assertEquals("2",scalar("SELECT attempt_count FROM serms.notification WHERE notification_id=?",notification));
    }

    @Test void bookingAndCancellationAuditAreAtomicAndAppendOnly() throws Exception {
        state("23514",()->repository.book(user,equipment,start,end,"Teaching",""));
        assertTrue(available());
        Reservation r=repository.book(user,equipment,start,end,"Teaching","request-"+equipment);
        assertEquals("Teaching",scalar("SELECT purpose FROM serms.reservation WHERE reservation_id=?",r.reservationId()));
        state("23514",()->repository.cancel(user,r.reservationId(),""));
        assertFalse(available());
        assertTrue(repository.cancel(user,r.reservationId()));
        assertEquals("2",scalar("SELECT count(*) FROM serms.audit_log WHERE entity_id=?",r.reservationId()));
        state("23514",()->execute("DELETE FROM serms.audit_log WHERE entity_id=?",r.reservationId()));
        state("23514",()->execute("UPDATE serms.audit_log SET outcome='CHANGED' WHERE entity_id=?",r.reservationId()));
    }

    @Test void versionPredicateRejectsStaleWriterAndRetiredIsTerminal() throws Exception {
        assertEquals(1,execute("UPDATE serms.equipment SET name='Updated' WHERE equipment_id=? AND version=0",equipment));
        assertEquals(0,execute("UPDATE serms.equipment SET name='Stale' WHERE equipment_id=? AND version=0",equipment));
        assertEquals("1",scalar("SELECT version FROM serms.equipment WHERE equipment_id=?",equipment));
        execute("UPDATE serms.equipment SET status='RETIRED' WHERE equipment_id=?",equipment);
        state("23514",()->execute("UPDATE serms.equipment SET status='AVAILABLE' WHERE equipment_id=?",equipment));
    }

    @Test void currentIntervalIsAcceptedButDisabledUserCannotCancel() throws Exception {
        Instant current=Instant.now().minusSeconds(10).truncatedTo(ChronoUnit.SECONDS);
        Reservation r=repository.book(user,equipment,current,end);
        execute("UPDATE serms.app_user SET account_status='DISABLED' WHERE user_id=?",user);
        state("23514",()->repository.cancel(user,r.reservationId()));
        assertEquals("CONFIRMED",scalar("SELECT status FROM serms.reservation WHERE reservation_id=?",r.reservationId()));
    }


    @Test void concurrentLoansForDifferentReservationsStillProtectOneEquipment() throws Exception {
        Reservation first=repository.book(user,equipment,start,end);
        Reservation second=repository.book(user,equipment,end,end.plusSeconds(3600));
        ExecutorService workers=Executors.newFixedThreadPool(2);
        CountDownLatch ready=new CountDownLatch(2), go=new CountDownLatch(1);
        java.util.function.Function<Reservation,Callable<String>> attempt = r -> () -> {
            ready.countDown();
            if (!go.await(10,TimeUnit.SECONDS)) throw new AssertionError("Start gate timed out");
            try {
                execute("""
                    INSERT INTO serms.loan(loan_id,reservation_id,checkout_by,checked_out_at,due_at)
                    VALUES (?,?,?,?,?)
                    """,UUID.randomUUID(),r.reservationId(),otherUser,
                    Timestamp.from(Instant.now().truncatedTo(ChronoUnit.SECONDS)),Timestamp.from(r.endAt()));
                return "OK";
            } catch(SQLException e) { return e.getSQLState(); }
        };
        try {
            Future<String> a=workers.submit(attempt.apply(first)), b=workers.submit(attempt.apply(second));
            assertTrue(ready.await(10,TimeUnit.SECONDS)); go.countDown();
            var results=java.util.List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));
            assertEquals(1,results.stream().filter("OK"::equals).count());
            assertEquals(1,results.stream().filter("23514"::equals).count());
        } finally { go.countDown(); workers.shutdownNow(); }
    }

}