package sg.edu.nus.serms.integration.notifications;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import sg.edu.nus.serms.notification.domain.*;
import sg.edu.nus.serms.notification.repository.*;
import sg.edu.nus.serms.notification.service.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes=NotificationContractTest.TestApplication.class,
    properties={"serms.notifications.worker-enabled=false","spring.jpa.open-in-view=false"})
@ActiveProfiles("serms-postgres")
class NotificationContractTest {
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan("sg.edu.nus.serms.notification")
    @EntityScan(basePackageClasses=Notification.class)
    @EnableJpaRepositories(basePackageClasses=NotificationRepository.class)
    @Import(NotificationBridgeConfiguration.class)
    static class TestApplication {
        @Bean MutableClock clock() { return new MutableClock(); }
    }

    static class MutableClock extends Clock {
        volatile Instant time=Instant.now();
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return time; }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",()->Objects.requireNonNull(System.getenv("SERMS_TEST_JDBC_URL")));
        r.add("spring.datasource.username",()->System.getenv("SERMS_TEST_DB_USER"));
        r.add("spring.datasource.password",()->System.getenv("SERMS_TEST_DB_PASSWORD"));
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired PlatformTransactionManager manager;
    @Autowired LoanReminderGuard guard;
    @Autowired NotificationRequestStore store;
    @Autowired LoanNotificationRequestFactory factory;
    @Autowired NotificationRequestService requests;
    @Autowired NotificationDeliveryService delivery;
    @Autowired NotificationRepository repository;
    @Autowired NotificationInboxService inbox;
    @Autowired ApplicationEventPublisher events;
    UUID user, equipment, reservation, loan;
    long reminderId;
    String principal;
    Instant now,due;

    TransactionTemplate tx() { return new TransactionTemplate(manager); }

    @BeforeEach void fixtures() {
        now=Instant.now().truncatedTo(ChronoUnit.MICROS); due=now.plusSeconds(3600); clock.time=now;
        user=UUID.randomUUID(); equipment=UUID.randomUUID(); reservation=UUID.randomUUID(); loan=UUID.randomUUID();
        principal=user.toString();
        tx().executeWithoutResult(status->{
            jdbc.update("INSERT INTO serms.app_user(user_id,email,display_name,password_hash) VALUES (?,?,?,'test-only')",
                user,user+"@example.invalid","Borrower");
            jdbc.update("INSERT INTO serms.equipment(equipment_id,asset_tag,name,category,location) VALUES (?,?,'Camera','Camera','Lab')",
                equipment,equipment.toString());
            jdbc.update("""
                INSERT INTO serms.reservation(reservation_id,requester_id,equipment_id,start_at,end_at,status)
                VALUES (?,?,?,?,?,'CONFIRMED')
                """,reservation,user,equipment,java.sql.Timestamp.from(now.minusSeconds(60)),java.sql.Timestamp.from(due));
            jdbc.update("""
                INSERT INTO serms.loan(loan_id,reservation_id,checkout_by,checked_out_at,due_at) VALUES (?,?,?,?,?)
                """,loan,reservation,user,java.sql.Timestamp.from(now.minusSeconds(30)),java.sql.Timestamp.from(due));
            jdbc.update("UPDATE serms.equipment SET status='ON_LOAN' WHERE equipment_id=?",equipment);
            jdbc.update("UPDATE serms.reservation SET status='FULFILLED' WHERE reservation_id=?",reservation);
        });
        reminderId=jdbc.queryForObject("SELECT reminder_id FROM serms.loan WHERE loan_id=?",Long.class,loan);
    }

    LoanReminderGuard.Result check(String recipient, Instant expected, NotificationType type, Instant time) {
        return tx().execute(s->guard.check(reminderId,recipient,expected,type,time));
    }

    NotificationRequest reminder(NotificationType type) {
        return tx().execute(s->factory.forLoan(loan,type,"once","Please return the camera."));
    }

    void returnLoanInsideTransaction() {
        jdbc.queryForObject("SELECT equipment_id FROM serms.equipment WHERE equipment_id=? FOR UPDATE",UUID.class,equipment);
        jdbc.update("""
            UPDATE serms.loan SET status='RETURNED',return_by=?,returned_at=?,return_condition='GOOD' WHERE loan_id=?
            """,user,java.sql.Timestamp.from(now),loan);
        jdbc.update("UPDATE serms.equipment SET status='AVAILABLE' WHERE equipment_id=?",equipment);
    }

    @Test void selectsAdaptersAndPreservesPublicIdentityContract() {
        assertInstanceOf(PostgresLoanReminderGuard.class,guard);
        assertInstanceOf(PostgresNotificationRequestStore.class,store);
        var request=reminder(NotificationType.DUE_SOON);
        assertEquals(principal,request.recipient());
        assertEquals(reminderId,request.loanId());
        assertEquals(due,request.expectedDueAt());
        assertEquals("loan:"+loan,request.sourceId());
        assertEquals(64,request.dedupKey().length());
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,
            ()->guard.check(reminderId,principal,due,NotificationType.DUE_SOON,now));
    }

    @Test void guardRechecksOwnerDueDateAndReminderBoundaries() {
        assertEquals(LoanReminderGuard.Result.NOT_APPLICABLE,check("wrong",due,NotificationType.DUE_SOON,now));
        assertEquals(LoanReminderGuard.Result.DUE_DATE_CHANGED,check(principal,due.plusSeconds(1),NotificationType.DUE_SOON,now));
        assertEquals(LoanReminderGuard.Result.NOT_APPLICABLE,check(principal,due,NotificationType.DUE_SOON,due.minusSeconds(86401)));
        assertEquals(LoanReminderGuard.Result.VALID,check(principal,due,NotificationType.DUE_SOON,due.minusSeconds(86400)));
        assertEquals(LoanReminderGuard.Result.VALID,check(principal,due,NotificationType.DUE_SOON,due));
        assertEquals(LoanReminderGuard.Result.NOT_APPLICABLE,check(principal,due,NotificationType.OVERDUE,due));
        assertEquals(LoanReminderGuard.Result.VALID,check(principal,due,NotificationType.OVERDUE,due.plusNanos(1000)));
        assertEquals(LoanReminderGuard.Result.NOT_APPLICABLE,
            tx().execute(s->guard.check(Long.MAX_VALUE,principal,due,NotificationType.OVERDUE,now)));
    }

    @Test void principalIsExplicitCaseSensitiveAndNotEmail() {
        jdbc.update("UPDATE serms.app_user SET notification_principal=? WHERE user_id=?","Borrower:"+user,user);
        assertEquals(LoanReminderGuard.Result.VALID,check("Borrower:"+user,due,NotificationType.DUE_SOON,now));
        assertEquals(LoanReminderGuard.Result.NOT_APPLICABLE,check("borrower:"+user,due,NotificationType.DUE_SOON,now));
        assertEquals(LoanReminderGuard.Result.NOT_APPLICABLE,check(user+"@example.invalid",due,NotificationType.DUE_SOON,now));
    }

    @Test void actualUpstreamRequestsDeduplicateConcurrentlyAndRetainOriginalContent() throws Exception {
        var r=reminder(NotificationType.DUE_SOON);
        ExecutorService pool=Executors.newFixedThreadPool(4);
        try {
            var tasks=new ArrayList<Callable<String>>();
            for(int i=0;i<8;i++) tasks.add(()->requests.requestOnce(r));
            Set<String> ids=new HashSet<>();
            for(var result:pool.invokeAll(tasks)) ids.add(result.get(15,TimeUnit.SECONDS));
            assertEquals(1,ids.size());
            var changed=new NotificationRequest(r.recipient(),r.type(),r.sourceId(),r.period(),r.loanId(),r.expectedDueAt(),"Changed");
            assertEquals(ids.iterator().next(),requests.requestOnce(changed));
            assertEquals(r.content(),repository.findById(ids.iterator().next()).orElseThrow().getContent());
        } finally { pool.shutdownNow(); }
    }

    @Test void actualUpstreamEventJoinsProducerTransactionAndRollbackRemovesBothChanges() {
        var r=reminder(NotificationType.DUE_SOON);
        tx().executeWithoutResult(status->{
            jdbc.update("UPDATE serms.equipment SET name='Rolled back' WHERE equipment_id=?",equipment);
            events.publishEvent(new NotificationRequested(r));
            status.setRollbackOnly();
        });
        assertEquals("Camera",jdbc.queryForObject("SELECT name FROM serms.equipment WHERE equipment_id=?",String.class,equipment));
        assertTrue(repository.findByDedupKey(r.dedupKey()).isEmpty());
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,
            ()->events.publishEvent(new NotificationRequested(r)));
        tx().executeWithoutResult(s->events.publishEvent(new NotificationRequested(r)));
        assertEquals(Notification.Status.PENDING,repository.findByDedupKey(r.dedupKey()).orElseThrow().getStatus());
    }

    @Test void actualUpstreamDeliveryAndReadUseSameSchemaAndAttemptHistory() {
        String id=requests.requestOnce(reminder(NotificationType.DUE_SOON));
        assertTrue(inbox.inbox(principal,0).isEmpty());
        delivery.deliver(id); delivery.deliver(id);
        assertEquals(Notification.Status.DELIVERED,repository.findById(id).orElseThrow().getStatus());
        assertEquals(1,inbox.inbox(principal,0).size());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM serms.notification_attempt WHERE notification_id=?",Integer.class,id));
        inbox.markRead(id,principal);
        assertEquals(now,repository.findById(id).orElseThrow().getReadAt());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->inbox.markRead(id,"other"));
    }

    @Test void returnedAndChangedDueRequestsCancelWithoutVisibility() {
        var r=reminder(NotificationType.DUE_SOON);
        var stale=new NotificationRequest(r.recipient(),r.type(),r.sourceId(),r.period(),r.loanId(),due.plusSeconds(1),r.content());
        String staleId=requests.requestOnce(stale); delivery.deliver(staleId);
        assertEquals("DUE_DATE_CHANGED",repository.findById(staleId).orElseThrow().getReason());
        String id=requests.requestOnce(r);
        tx().executeWithoutResult(s->returnLoanInsideTransaction());
        delivery.deliver(id);
        assertEquals(Notification.Status.CANCELLED,repository.findById(id).orElseThrow().getStatus());
        assertEquals("RETURNED",repository.findById(id).orElseThrow().getReason());
        assertTrue(inbox.inbox(principal,0).isEmpty());
    }

    @Test void equipmentMaintenanceDoesNotPretendOutstandingLoanWasReturned() {
        jdbc.update("UPDATE serms.equipment SET status='UNDER_MAINTENANCE' WHERE equipment_id=?",equipment);
        clock.time=due.plusSeconds(1);
        assertEquals(LoanReminderGuard.Result.VALID,check(principal,due,NotificationType.OVERDUE,clock.time));
        String id=requests.requestOnce(reminder(NotificationType.OVERDUE)); delivery.deliver(id);
        assertEquals(Notification.Status.DELIVERED,repository.findById(id).orElseThrow().getStatus());
    }

    @Test void committedReturnWinsRaceAndGuardRetainsBusinessLocks() throws Exception {
        String id=requests.requestOnce(reminder(NotificationType.DUE_SOON));
        ExecutorService pool=Executors.newFixedThreadPool(2);
        CountDownLatch returnedButUncommitted=new CountDownLatch(1), allowCommit=new CountDownLatch(1), started=new CountDownLatch(1);
        try {
            Future<?> returning=pool.submit(()->tx().executeWithoutResult(s->{
                returnLoanInsideTransaction();
                returnedButUncommitted.countDown();
                try { if(!allowCommit.await(10,TimeUnit.SECONDS)) throw new AssertionError("Return commit gate timed out"); }
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
            }));
            assertTrue(returnedButUncommitted.await(10,TimeUnit.SECONDS));
            Future<?> delivering=pool.submit(()->{started.countDown();delivery.deliver(id);});
            assertTrue(started.await(10,TimeUnit.SECONDS));
            assertThrows(TimeoutException.class,()->delivering.get(250,TimeUnit.MILLISECONDS));
            allowCommit.countDown();
            returning.get(15,TimeUnit.SECONDS); delivering.get(15,TimeUnit.SECONDS);
            assertEquals("RETURNED",repository.findById(id).orElseThrow().getReason());
            assertTrue(inbox.inbox(principal,0).isEmpty());
        } finally {allowCommit.countDown();pool.shutdownNow();}
    }

    @Test void concurrentUpstreamDeliveryCreatesOnlyOneVisibleResult() throws Exception {
        String id=requests.requestOnce(reminder(NotificationType.DUE_SOON));
        ExecutorService pool=Executors.newFixedThreadPool(4);
        try {
            List<Callable<Void>> tasks=new ArrayList<>();
            for(int i=0;i<8;i++) tasks.add(()->{delivery.deliver(id);return null;});
            for(var result:pool.invokeAll(tasks)) result.get(15,TimeUnit.SECONDS);
            assertEquals(1,repository.findById(id).orElseThrow().getAttempts());
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM serms.notification_attempt WHERE notification_id=?",Integer.class,id));
        } finally {pool.shutdownNow();}
        tx().executeWithoutResult(s->returnLoanInsideTransaction());
        delivery.deliver(id);
        assertEquals(Notification.Status.DELIVERED,repository.findById(id).orElseThrow().getStatus());
    }

    @Test void foreignKeysRejectMissingRecipientAndLoanAndIdMappingCannotChange() {
        var r=reminder(NotificationType.DUE_SOON);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->requests.requestOnce(
            new NotificationRequest("unknown",r.type(),r.sourceId(),r.period(),r.loanId(),r.expectedDueAt(),r.content())));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->requests.requestOnce(
            new NotificationRequest(r.recipient(),r.type(),r.sourceId(),r.period(),Long.MAX_VALUE,r.expectedDueAt(),r.content())));
        assertThrows(org.springframework.jdbc.BadSqlGrammarException.class,
            ()->jdbc.update("UPDATE serms.loan SET reminder_id=reminder_id+100000 WHERE loan_id=?",loan));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
            ()->jdbc.update("UPDATE serms.loan SET reminder_id=DEFAULT WHERE loan_id=?",loan));
        assertEquals(reminderId,jdbc.queryForObject("SELECT reminder_id FROM serms.loan WHERE loan_id=?",Long.class,loan));
    }

    @Test void existingV2NotificationsUpgradeWithoutLosingHistory() {
        String url=Objects.requireNonNull(System.getenv("SERMS_UPGRADE_JDBC_URL"));
        var upgrade=new JdbcTemplate(new DriverManagerDataSource(url,System.getenv("SERMS_TEST_DB_USER"),System.getenv("SERMS_TEST_DB_PASSWORD")));
        assertEquals("serms_upgrade",upgrade.queryForObject("SELECT current_database()",String.class));
        var flyway=Flyway.configure().dataSource(url,System.getenv("SERMS_TEST_DB_USER"),System.getenv("SERMS_TEST_DB_PASSWORD"))
            .defaultSchema("public").table("serms_flyway_history").baselineVersion("2")
            .locations("classpath:db/serms-shared","classpath:db/serms-notification").load();
        flyway.baseline(); flyway.migrate(); flyway.validate();
        assertEquals(3,upgrade.queryForObject("SELECT max(version) FROM serms.schema_version",Integer.class));
        assertEquals(3,upgrade.queryForObject("SELECT count(*) FROM serms.notification_legacy_v2",Integer.class));
        assertEquals(3,upgrade.queryForObject("SELECT count(*) FROM serms.notification_request",Integer.class));
        assertEquals("DELIVERED",upgrade.queryForObject("""
            SELECT status FROM serms.notification_request WHERE id='20000000-0000-0000-0000-000000000005'
            """,String.class));
        assertEquals(1,upgrade.queryForObject("""
            SELECT count(*) FROM serms.notification_request n JOIN serms.loan l ON l.reminder_id=n.loan_id
            WHERE n.id='20000000-0000-0000-0000-000000000005'
              AND n.expected_due_at=l.due_at AND n.read_at IS NOT NULL AND n.attempts=2
            """,Integer.class));
        assertEquals(0,upgrade.queryForObject("SELECT count(*) FROM serms.notification_attempt",Integer.class));
        var canonicalKey=upgrade.queryForObject("""
            SELECT l.reminder_id,l.due_at,u.notification_principal FROM serms.loan l
            JOIN serms.reservation r USING(reservation_id)
            JOIN serms.app_user u ON u.user_id=r.requester_id
            WHERE l.loan_id='20000000-0000-0000-0000-000000000004'
            """,(r,n)->new NotificationRequest(r.getString(3),NotificationType.DUE_SOON,
                "loan:20000000-0000-0000-0000-000000000004","once",r.getLong(1),
                r.getTimestamp(2).toInstant(),"Ignored content").dedupKey());
        assertEquals(canonicalKey,upgrade.queryForObject("""
            SELECT dedup_key FROM serms.notification_request WHERE id='20000000-0000-0000-0000-000000000005'
            """,String.class));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
            ()->upgrade.update("DELETE FROM serms.notification_legacy_v2"));
    }
}