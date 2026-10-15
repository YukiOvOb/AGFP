package sg.edu.nus.serms.reservation;

import static org.assertj.core.api.Assertions.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import sg.edu.nus.serms.equipment.domain.EquipmentSearchCriteria;
import sg.edu.nus.serms.equipment.domain.EquipmentSummary;
import sg.edu.nus.serms.equipment.domain.TimeWindow;
import sg.edu.nus.serms.equipment.repository.EquipmentSearchRepository;
import sg.edu.nus.serms.equipment.service.EquipmentSearchService;
import sg.edu.nus.serms.reservation.domain.ReservationRequest;
import sg.edu.nus.serms.reservation.domain.ReservationStatus;
import sg.edu.nus.serms.reservation.repository.ReservationRepository;
import sg.edu.nus.serms.reservation.domain.PendingReservation;
import sg.edu.nus.serms.reservation.service.DefaultReservationApprovalService;
import sg.edu.nus.serms.reservation.service.ReservationApprovalAdapter;
import sg.edu.nus.serms.reservation.service.ReservationApprovalService;
import sg.edu.nus.serms.reservation.service.ReservationExpiredException;
import sg.edu.nus.serms.reservation.service.ReservationNotFoundException;
import sg.edu.nus.serms.reservation.service.ReservationStateConflictException;
import sg.edu.nus.serms.reservation.service.ReservationVersionConflictException;
import sg.edu.nus.serms.reservation.service.ReservationConfiguration;
import sg.edu.nus.serms.reservation.service.ReservationException;
import sg.edu.nus.serms.reservation.service.ReservationLimits;
import sg.edu.nus.serms.reservation.service.ReservationService;
import sg.edu.nus.serms.reservation.service.event.ReservationCancelled;
import sg.edu.nus.serms.reservation.service.event.ReservationSubmitted;
import sg.edu.nus.serms.approval.service.ApprovalConflictException;
import sg.edu.nus.serms.shared.domain.RecordNotFoundException;
import sg.edu.nus.serms.testsupport.SermsFixtures;
import sg.edu.nus.serms.testsupport.SermsPostgres;

/**
 * UC01 test bench against a real PostgreSQL 18 with the V001 + V002 schema: search, availability,
 * booking rules, the exclusion constraint under concurrency, cancellation and the UC02
 * ReservationApprovalService contract (auth-and-approval.md §3–4).
 * Method security is covered separately in {@code ReservationServiceSecurityTest}.
 */
@SpringJUnitConfig(ReservationPostgresTest.Config.class)
class ReservationPostgresTest {
  static final ReservationLimits LIMITS =
      new ReservationLimits(Duration.ofMinutes(30), Duration.ofDays(7), Duration.ofDays(90), 3, true);

  @TestConfiguration
  @EnableTransactionManagement(proxyTargetClass = true)
  @Import({EquipmentSearchRepository.class, EquipmentSearchService.class, ReservationRepository.class})
  static class Config {
    @Bean
    DataSource dataSource() {
      return SermsPostgres.dataSource();
    }

    @Bean
    JdbcTemplate jdbcTemplate(DataSource ds) {
      return new JdbcTemplate(ds);
    }

    @Bean
    NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource ds) {
      return new NamedParameterJdbcTemplate(ds);
    }

    @Bean
    PlatformTransactionManager transactionManager(DataSource ds) {
      return new DataSourceTransactionManager(ds);
    }

    @Bean
    Events events() {
      return new Events();
    }

    @Bean
    ReservationService reservationService(
        ReservationRepository repository,
        org.springframework.context.ApplicationEventPublisher publisher) {
      return new ReservationService(
          repository,
          ReservationConfiguration.rulesFor(LIMITS),
          publisher,
          Clock.systemUTC());
    }

    @Bean
    ReservationApprovalService reservationApprovalService(ReservationRepository repository) {
      return new DefaultReservationApprovalService(repository, Clock.systemUTC());
    }

    @Bean
    ReservationApprovalAdapter reservationApprovalAdapter(ReservationApprovalService service) {
      return new ReservationApprovalAdapter(service);
    }
  }

  static class Events {
    final List<Object> received = new CopyOnWriteArrayList<>();

    @EventListener
    void on(ReservationSubmitted e) {
      received.add(e);
    }

    @EventListener
    void on(ReservationCancelled e) {
      received.add(e);
    }
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired ReservationService reservations;
  @Autowired ReservationRepository repository;
  @Autowired EquipmentSearchService search;
  @Autowired ReservationApprovalService approvalService;
  @Autowired ReservationApprovalAdapter approvalPort;
  @Autowired PlatformTransactionManager transactions;
  @Autowired Events events;
  SermsFixtures fixtures;
  UUID borrower;
  /** Unique per test so searches only see this test's rows. */
  String category;
  /** Whole hours, a few days ahead, so rule and trigger checks see a future start. */
  Instant base;

  @BeforeEach
  void setUp() {
    fixtures = new SermsFixtures(jdbc);
    borrower = fixtures.user("BORROWER");
    category = "Camera-" + UUID.randomUUID();
    base = Instant.now().truncatedTo(ChronoUnit.HOURS).plus(3, ChronoUnit.DAYS);
    events.received.clear();
  }

  Instant at(int hours) {
    return base.plus(hours, ChronoUnit.HOURS);
  }

  UUID camera() {
    return fixtures.equipment("Canon EOS R6", category, "COM1 Lab", false);
  }

  ReservationRequest request(UUID equipment, int fromHour, int toHour) {
    return new ReservationRequest(equipment, at(fromHour), at(toHour), "Project shoot");
  }

  @Nested
  class Search {
    EquipmentSearchCriteria criteria(String keyword, Instant from, Instant to, boolean onlyFree) {
      return new EquipmentSearchCriteria(keyword, category, null, from, to, onlyFree, 0, 20);
    }

    @Test
    void filtersByKeywordCategoryAndLocationAndHidesRetiredEquipment() {
      UUID canon = fixtures.equipment("Canon EOS R6", category, "COM1 Lab", false);
      UUID sony = fixtures.equipment("Sony A7 IV", category, "UTown Studio", false);
      UUID retired = fixtures.equipment("Canon 5D (old)", category, "COM1 Lab", false);
      fixtures.equipmentStatus(retired, "RETIRED");
      fixtures.equipment("Canon tripod", "Tripod-" + UUID.randomUUID(), "COM1 Lab", false);

      assertThat(ids(search.search(criteria("canon", null, null, false)).items())).containsExactly(canon);
      assertThat(ids(search.search(criteria(null, null, null, false)).items()))
          .containsExactlyInAnyOrder(canon, sony);
      var byLocation =
          search.search(new EquipmentSearchCriteria(null, category, "UTown Studio", null, null, false, 0, 20));
      assertThat(ids(byLocation.items())).containsExactly(sony);
      assertThat(search.search(criteria(null, null, null, false)).items())
          .allSatisfy(e -> assertThat(e.availableInWindow()).isNull());
      assertThat(search.filterOptions().categories()).contains(category);
    }

    @Test
    void wildcardCharactersInTheKeywordAreLiteral() {
      UUID percent = fixtures.equipment("Lens 100% sharp", category, "COM1 Lab", false);
      fixtures.equipment("Lens 1000 sharp", category, "COM1 Lab", false);
      assertThat(ids(search.search(criteria("100%", null, null, false)).items())).containsExactly(percent);
    }

    @Test
    void reportsAvailabilityForTheRequestedWindowAndCanShowOnlyFreeItems() {
      UUID booked = camera();
      UUID free = fixtures.equipment("Sony A7 IV", category, "COM1 Lab", false);
      UUID broken = fixtures.equipment("Nikon Z6", category, "COM1 Lab", false);
      fixtures.reservation(fixtures.user("BORROWER"), booked, at(2), at(6), "CONFIRMED");
      fixtures.openMaintenanceCase(broken, borrower);

      var all = search.search(criteria(null, at(4), at(8), false)).items();
      assertThat(all)
          .extracting(EquipmentSummary::equipmentId, EquipmentSummary::availableInWindow)
          .containsExactlyInAnyOrder(
              tuple(booked, false), tuple(free, true), tuple(broken, false));
      assertThat(ids(search.search(criteria(null, at(4), at(8), true)).items())).containsExactly(free);
      // [start, end) is half-open: a window starting when the booking ends is free.
      assertThat(ids(search.search(criteria(null, at(6), at(8), true)).items()))
          .containsExactlyInAnyOrder(booked, free);
    }

    @Test
    void availabilityListsHeldSlotsWithoutRevealingWhoHoldsThem() {
      UUID item = fixtures.equipment("Oscilloscope", category, "E4 Lab", true);
      UUID confirmed = fixtures.reservation(fixtures.user("BORROWER"), item, at(1), at(3), "PENDING_APPROVAL");
      jdbc.update("UPDATE serms.reservation SET status='CONFIRMED' WHERE reservation_id=?", confirmed);
      fixtures.reservation(fixtures.user("BORROWER"), item, at(5), at(7), "PENDING_APPROVAL");
      fixtures.reservation(fixtures.user("BORROWER"), item, at(30), at(31), "PENDING_APPROVAL");
      UUID cancelled = fixtures.reservation(fixtures.user("BORROWER"), item, at(8), at(9), "PENDING_APPROVAL");
      jdbc.update("UPDATE serms.reservation SET status='CANCELLED' WHERE reservation_id=?", cancelled);

      var a = search.availability(item, new TimeWindow(at(0), at(24)));
      assertThat(a.equipment().availableInWindow()).isFalse();
      assertThat(a.openMaintenance()).isFalse();
      assertThat(a.busySlots())
          .extracting(s -> s.startAt(), s -> s.reason())
          .containsExactly(tuple(at(1), "RESERVED"), tuple(at(5), "PENDING_APPROVAL"));
    }

    @Test
    void retiredOrUnknownEquipmentHasNoAvailabilityPage() {
      UUID retired = camera();
      fixtures.equipmentStatus(retired, "RETIRED");
      var window = new TimeWindow(at(0), at(1));
      assertThatThrownBy(() -> search.availability(retired, window)).isInstanceOf(RecordNotFoundException.class);
      assertThatThrownBy(() -> search.availability(UUID.randomUUID(), window))
          .isInstanceOf(RecordNotFoundException.class);
    }

    @Test
    void pagesThroughResults() {
      for (int i = 0; i < 5; i++) fixtures.equipment("Mic " + i, category, "COM1 Lab", false);
      var page = search.search(new EquipmentSearchCriteria(null, category, null, null, null, false, 1, 2));
      assertThat(page.items()).extracting(EquipmentSummary::name).containsExactly("Mic 2", "Mic 3");
      assertThat(page.totalElements()).isEqualTo(5);
      assertThat(page.totalPages()).isEqualTo(3);
      var past = search.search(new EquipmentSearchCriteria(null, category, null, null, null, false, 9, 2));
      assertThat(past.items()).isEmpty();
      assertThat(past.totalElements()).isEqualTo(5);
    }
  }

  @Nested
  class Submit {
    @Test
    void equipmentWithoutApprovalIsConfirmedImmediately() {
      UUID item = camera();
      var r = reservations.submit(borrower, request(item, 1, 3));
      assertThat(r.status()).isEqualTo(ReservationStatus.CONFIRMED);
      assertThat(r.requesterId()).isEqualTo(borrower);
      assertThat(r.startAt()).isEqualTo(at(1));
      assertThat(r.purpose()).isEqualTo("Project shoot");
      assertThat(r.cancellable()).isTrue();
      assertThat(events.received)
          .singleElement()
          .isInstanceOfSatisfying(
              ReservationSubmitted.class,
              e -> {
                assertThat(e.reservationId()).isEqualTo(r.reservationId());
                assertThat(e.status()).isEqualTo(ReservationStatus.CONFIRMED);
              });
    }

    @Test
    void equipmentThatNeedsApprovalStartsPending() {
      UUID item = fixtures.equipment("Oscilloscope", category, "E4 Lab", true);
      assertThat(reservations.submit(borrower, request(item, 1, 3)).status())
          .isEqualTo(ReservationStatus.PENDING_APPROVAL);
    }

    @Test
    void overlappingSlotIsRefusedButAdjacentSlotIsAccepted() {
      UUID item = camera();
      reservations.submit(fixtures.user("BORROWER"), request(item, 2, 6));
      assertThatThrownBy(() -> reservations.submit(borrower, request(item, 5, 8)))
          .isInstanceOf(ReservationException.SlotTaken.class);
      assertThatThrownBy(() -> reservations.submit(borrower, request(item, 1, 3)))
          .isInstanceOf(ReservationException.SlotTaken.class);
      assertThat(reservations.submit(borrower, request(item, 6, 8)).status())
          .isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void pendingReservationsAlsoHoldTheSlot() {
      UUID item = fixtures.equipment("Oscilloscope", category, "E4 Lab", true);
      reservations.submit(fixtures.user("BORROWER"), request(item, 2, 6));
      assertThatThrownBy(() -> reservations.submit(borrower, request(item, 3, 4)))
          .isInstanceOf(ReservationException.SlotTaken.class);
    }

    @Test
    void parallelRequestsForTheSameSlotBookItExactlyOnce() throws Exception {
      UUID item = camera();
      int threads = 8;
      var barrier = new CyclicBarrier(threads);
      var pool = Executors.newFixedThreadPool(threads);
      try {
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
          UUID person = fixtures.user("BORROWER");
          int offset = i % 2; // overlapping but not identical slots
          Callable<String> attempt =
              () -> {
                barrier.await(10, TimeUnit.SECONDS);
                try {
                  reservations.submit(person, request(item, 1 + offset, 4 + offset));
                  return "booked";
                } catch (ReservationException.SlotTaken e) {
                  return "conflict";
                }
              };
          results.add(pool.submit(attempt));
        }
        List<String> outcomes = new ArrayList<>();
        for (var f : results) outcomes.add(f.get(30, TimeUnit.SECONDS));
        assertThat(outcomes).containsOnly("booked", "conflict");
        assertThat(outcomes).filteredOn("booked"::equals).hasSize(1);
      } finally {
        pool.shutdownNow();
      }
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM serms.reservation WHERE equipment_id = ?", Integer.class, item))
          .isEqualTo(1);
    }

    @Test
    void equipmentUnderMaintenanceOrWithAnOpenFaultCannotBeReserved() {
      UUID maintained = camera();
      fixtures.equipmentStatus(maintained, "UNDER_MAINTENANCE");
      assertThatThrownBy(() -> reservations.submit(borrower, request(maintained, 1, 2)))
          .isInstanceOf(ReservationException.EquipmentUnavailable.class)
          .hasMessageContaining("under maintenance");

      UUID faulty = camera();
      fixtures.openMaintenanceCase(faulty, borrower);
      assertThatThrownBy(() -> reservations.submit(borrower, request(faulty, 1, 2)))
          .isInstanceOf(ReservationException.EquipmentUnavailable.class)
          .hasMessageContaining("fault");
    }

    @Test
    void retiredOrUnknownEquipmentIsNotFound() {
      UUID retired = camera();
      fixtures.equipmentStatus(retired, "RETIRED");
      assertThatThrownBy(() -> reservations.submit(borrower, request(retired, 1, 2)))
          .isInstanceOf(RecordNotFoundException.class);
      assertThatThrownBy(() -> reservations.submit(borrower, request(UUID.randomUUID(), 1, 2)))
          .isInstanceOf(RecordNotFoundException.class);
    }

    @Test
    void equipmentStillOnLoanAtTheRequestedStartIsUnavailable() {
      UUID item = camera();
      UUID earlier =
          fixtures.reservation(fixtures.user("BORROWER"), item, Instant.now().plusSeconds(60), at(5), "CONFIRMED");
      fixtures.checkOut(earlier, fixtures.user("CUSTODIAN"));
      fixtures.equipmentStatus(item, "ON_LOAN");
      // Starts after the loan is due back: allowed by serms.equipment_can_reserve.
      assertThat(reservations.submit(borrower, request(item, 6, 8)).status())
          .isEqualTo(ReservationStatus.CONFIRMED);
      // Overlaps the loan's reservation slot.
      assertThatThrownBy(() -> reservations.submit(borrower, request(item, 4, 5)))
          .isInstanceOf(ReservationException.SlotTaken.class);
    }

    @Test
    void anItemsMaximumBorrowingDurationIsEnforced() {
      UUID item = camera();
      jdbc.update("UPDATE serms.equipment SET max_borrow_duration = '2 hours' WHERE equipment_id = ?", item);
      assertThatThrownBy(() -> reservations.submit(borrower, request(item, 1, 4)))
          .isInstanceOfSatisfying(
              ReservationException.RulesBroken.class,
              e -> {
                assertThat(e.violations()).extracting(v -> v.code()).containsExactly("too-long");
                assertThat(e.violations().get(0).message())
                    .isEqualTo("This equipment may be reserved for at most 2 hours.");
              });
      assertThat(reservations.submit(borrower, request(item, 1, 3)).status())
          .isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void bookingRulesAreReportedTogether() {
      UUID item = camera();
      var tooLong = new ReservationRequest(item, at(1), at(1).plus(8, ChronoUnit.DAYS), null);
      assertThatThrownBy(() -> reservations.submit(borrower, tooLong))
          .isInstanceOfSatisfying(
              ReservationException.RulesBroken.class,
              e -> assertThat(e.violations()).extracting(v -> v.code()).containsExactly("too-long"));
      var past = new ReservationRequest(item, Instant.now().minusSeconds(3600), at(1), null);
      assertThatThrownBy(() -> reservations.submit(borrower, past))
          .isInstanceOfSatisfying(
              ReservationException.RulesBroken.class,
              e -> assertThat(e.violations()).extracting(v -> v.code()).contains("start-in-past"));
      assertThat(jdbc.queryForObject(
              "SELECT count(*) FROM serms.reservation WHERE equipment_id = ?", Integer.class, item))
          .isZero();
    }

    @Test
    void quotaCountsOnlyOpenReservations() {
      for (int i = 0; i < 3; i++) reservations.submit(borrower, request(camera(), 1, 2));
      assertThatThrownBy(() -> reservations.submit(borrower, request(camera(), 1, 2)))
          .isInstanceOfSatisfying(
              ReservationException.RulesBroken.class,
              e -> assertThat(e.violations()).extracting(v -> v.code()).containsExactly("quota-exceeded"));
      var first = reservations.mine(borrower, null, 0, 10).items().get(0);
      reservations.cancel(borrower, first.reservationId());
      assertThat(reservations.submit(borrower, request(camera(), 1, 2)).status())
          .isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void disabledAccountsCannotReserve() {
      UUID disabled = fixtures.user("BORROWER", "DISABLED");
      assertThatThrownBy(() -> reservations.submit(disabled, request(camera(), 1, 2)))
          .isInstanceOf(ReservationException.RequesterNotEligible.class);
    }
  }

  /** Proposal rule 11 (overdue borrowers) and business rule 9 (who did what, and when). */
  @Nested
  class OverdueAndAudit {
    /** Same rules and repository, with a clock two hours ahead, inside a real transaction. */
    <T> T twoHoursLater(Function<ReservationService, T> call) {
      var later =
          new ReservationService(
              repository,
              ReservationConfiguration.rulesFor(LIMITS),
              event -> {},
              Clock.offset(Clock.systemUTC(), Duration.ofHours(2)));
      return new TransactionTemplate(transactions).execute(status -> call.apply(later));
    }

    /** A loan due in one hour: overdue for the clock two hours ahead. */
    UUID shortLoan(UUID requester) {
      UUID r =
          fixtures.reservation(
              requester, camera(), Instant.now().plusSeconds(60), Instant.now().plusSeconds(3600), "CONFIRMED");
      fixtures.checkOut(r, fixtures.user("CUSTODIAN"));
      return r;
    }

    int auditRows(UUID reservationId, String action) {
      return jdbc.queryForObject(
          "SELECT count(*) FROM serms.audit_log WHERE entity_type = 'Reservation'"
              + " AND entity_id = ? AND action = ?",
          Integer.class,
          reservationId,
          action);
    }

    @Test
    void borrowerWithAnOverdueLoanCannotReserveUntilItIsReturned() {
      UUID loanReservation = shortLoan(borrower);
      assertThat(repository.hasOverdueLoan(borrower, Instant.now())).isFalse();
      assertThat(repository.hasOverdueLoan(borrower, Instant.now().plus(Duration.ofHours(2)))).isTrue();
      UUID item = camera();
      assertThatThrownBy(() -> twoHoursLater(s -> s.submit(borrower, request(item, 1, 2))))
          .isInstanceOfSatisfying(
              ReservationException.RulesBroken.class,
              e -> assertThat(e.violations()).extracting(v -> v.code()).containsExactly("overdue-loan"));
      assertThat(jdbc.queryForObject(
              "SELECT count(*) FROM serms.reservation WHERE equipment_id = ?", Integer.class, item))
          .isZero();
      // Another borrower is not affected by this borrower's overdue loan.
      UUID other = fixtures.user("BORROWER");
      assertThat(twoHoursLater(s -> s.submit(other, request(item, 1, 2))).status())
          .isEqualTo(ReservationStatus.CONFIRMED);
      jdbc.update(
          "UPDATE serms.loan SET status = 'RETURNED', return_by = ?, returned_at = now(),"
              + " return_condition = 'GOOD' WHERE reservation_id = ?",
          fixtures.user("CUSTODIAN"),
          loanReservation);
      assertThat(twoHoursLater(s -> s.submit(borrower, request(camera(), 1, 2))).status())
          .isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void submitAndCancelAreAuditedWithTheBorrowerAsActor() {
      var r = reservations.submit(borrower, request(camera(), 1, 2));
      reservations.cancel(borrower, r.reservationId());
      assertThat(auditRows(r.reservationId(), "RESERVATION_SUBMIT")).isOne();
      assertThat(auditRows(r.reservationId(), "RESERVATION_CANCEL")).isOne();
      var rows =
          jdbc.queryForList(
              "SELECT actor_id, outcome, change_summary FROM serms.audit_log"
                  + " WHERE entity_id = ? ORDER BY occurred_at, action DESC",
              r.reservationId());
      assertThat(rows).extracting(row -> row.get("actor_id")).containsOnly(borrower);
      assertThat(rows).extracting(row -> row.get("outcome")).containsOnly("SUCCESS");
      assertThat(rows.get(0).get("change_summary").toString()).contains("status=CONFIRMED");
      assertThat(rows.get(1).get("change_summary").toString()).startsWith("from=CONFIRMED");
    }

    @Test
    void aRefusedBookingLeavesNoAuditRecord() {
      UUID item = camera();
      reservations.submit(borrower, request(item, 1, 3));
      UUID other = fixtures.user("BORROWER");
      assertThatThrownBy(() -> reservations.submit(other, request(item, 2, 4)))
          .isInstanceOf(ReservationException.SlotTaken.class);
      assertThat(jdbc.queryForObject(
              "SELECT count(*) FROM serms.audit_log WHERE actor_id = ?", Integer.class, other))
          .isZero();
    }
  }

  @Nested
  class Cancel {
    @Test
    void cancellingReleasesTheSlot() {
      UUID item = camera();
      var r = reservations.submit(borrower, request(item, 1, 3));
      events.received.clear();
      var cancelled = reservations.cancel(borrower, r.reservationId());
      assertThat(cancelled.status()).isEqualTo(ReservationStatus.CANCELLED);
      assertThat(cancelled.cancellable()).isFalse();
      assertThat(events.received).singleElement().isInstanceOf(ReservationCancelled.class);
      assertThat(reservations.submit(fixtures.user("BORROWER"), request(item, 1, 3)).status())
          .isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void pendingReservationCanBeCancelled() {
      UUID item = fixtures.equipment("Oscilloscope", category, "E4 Lab", true);
      var r = reservations.submit(borrower, request(item, 1, 3));
      assertThat(reservations.cancel(borrower, r.reservationId()).status())
          .isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void checkedOutReservationCannotBeCancelled() {
      UUID item = camera();
      UUID r = fixtures.reservation(borrower, item, Instant.now().plusSeconds(60), at(2), "CONFIRMED");
      fixtures.checkOut(r, fixtures.user("CUSTODIAN"));
      assertThatThrownBy(() -> reservations.cancel(borrower, r))
          .isInstanceOf(ReservationException.NotCancellable.class)
          .hasMessageContaining("checked out");
      assertThat(fixtures.reservationStatus(r)).isEqualTo("FULFILLED");
    }

    @Test
    void alreadyCancelledOrRejectedReservationCannotBeCancelledAgain() {
      var r = reservations.submit(borrower, request(camera(), 1, 2));
      reservations.cancel(borrower, r.reservationId());
      assertThatThrownBy(() -> reservations.cancel(borrower, r.reservationId()))
          .isInstanceOf(ReservationException.NotCancellable.class);
    }

    @Test
    void anotherBorrowersReservationLooksMissing() {
      var r = reservations.submit(borrower, request(camera(), 1, 2));
      UUID stranger = fixtures.user("BORROWER");
      assertThatThrownBy(() -> reservations.cancel(stranger, r.reservationId()))
          .isInstanceOf(RecordNotFoundException.class);
      assertThatThrownBy(() -> reservations.get(stranger, r.reservationId()))
          .isInstanceOf(RecordNotFoundException.class);
      assertThat(fixtures.reservationStatus(r.reservationId())).isEqualTo("CONFIRMED");
    }
  }

  @Nested
  class MyReservations {
    @Test
    void listsOnlyMyReservationsNewestStartFirstAndFiltersByStatus() {
      var early = reservations.submit(borrower, request(camera(), 1, 2));
      var late = reservations.submit(borrower, request(camera(), 10, 12));
      reservations.submit(fixtures.user("BORROWER"), request(camera(), 1, 2));
      reservations.cancel(borrower, early.reservationId());

      var all = reservations.mine(borrower, null, 0, 10);
      assertThat(all.totalElements()).isEqualTo(2);
      assertThat(all.items())
          .extracting(r -> r.reservationId())
          .containsExactly(late.reservationId(), early.reservationId());
      assertThat(reservations.mine(borrower, ReservationStatus.CANCELLED, 0, 10).items())
          .extracting(r -> r.reservationId())
          .containsExactly(early.reservationId());
      assertThat(reservations.get(borrower, late.reservationId()).equipmentName())
          .isEqualTo("Canon EOS R6");
    }
  }

  @Nested
  class ApprovalContract {
    UUID pendingItem() {
      return fixtures.equipment("Oscilloscope", category, "E4 Lab", true);
    }

    <T> T inTransaction(java.util.function.Supplier<T> work) {
      return new TransactionTemplate(transactions).execute(status -> work.get());
    }

    @Test
    void pendingListShowsRequesterEquipmentAndExpiry() {
      UUID item = pendingItem();
      var future = reservations.submit(borrower, request(item, 1, 3));
      UUID expired =
          fixtures.reservation(
              fixtures.user("BORROWER"), item, Instant.now().minusSeconds(3600), Instant.now().plusSeconds(1800),
              "PENDING_APPROVAL");
      var view = approvalService.get(future.reservationId());
      assertThat(view.requesterName()).isEqualTo("UC01 test user");
      assertThat(view.equipment().category()).isEqualTo(category);
      assertThat(view.expired()).isFalse();
      assertThat(approvalService.get(expired).expired()).isTrue();
      var page = approvalService.findPendingApproval(org.springframework.data.domain.PageRequest.of(0, 500));
      assertThat(page.getContent()).extracting(PendingReservation::reservationId)
          .contains(future.reservationId(), expired);
      assertThat(page.getContent()).allSatisfy(p -> assertThat(p.status()).isEqualTo(ReservationStatus.PENDING_APPROVAL));
      assertThatThrownBy(() -> approvalService.get(UUID.randomUUID()))
          .isInstanceOf(ReservationNotFoundException.class);
    }

    @Test
    void confirmAndRejectMustRunInsideTheApproversTransaction() {
      var r = reservations.submit(borrower, request(pendingItem(), 1, 3));
      assertThatThrownBy(() -> approvalService.confirm(r.reservationId(), r.version()))
          .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
      assertThatThrownBy(() -> approvalService.reject(r.reservationId(), r.version()))
          .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @Test
    void confirmChecksVersionAndStateThenConfirms() {
      var r = reservations.submit(borrower, request(pendingItem(), 1, 3));
      assertThatThrownBy(() -> inTransaction(() -> approvalService.confirm(r.reservationId(), r.version() + 1)))
          .isInstanceOfSatisfying(
              ReservationVersionConflictException.class,
              e -> {
                assertThat(e.currentVersion()).isEqualTo(r.version());
                assertThat(e.currentStatus()).isEqualTo(ReservationStatus.PENDING_APPROVAL);
              });
      var confirmed = inTransaction(() -> approvalService.confirm(r.reservationId(), r.version()));
      assertThat(confirmed.status()).isEqualTo(ReservationStatus.CONFIRMED);
      assertThat(confirmed.version()).isGreaterThan(r.version());
      assertThatThrownBy(() -> inTransaction(() -> approvalService.reject(r.reservationId(), confirmed.version())))
          .isInstanceOfSatisfying(
              ReservationStateConflictException.class,
              e -> assertThat(e.currentStatus()).isEqualTo(ReservationStatus.CONFIRMED));
    }

    @Test
    void expiredRequestsCanOnlyBeRejected() {
      UUID item = pendingItem();
      UUID expired =
          fixtures.reservation(
              borrower, item, Instant.now().minusSeconds(3600), Instant.now().plusSeconds(1800), "PENDING_APPROVAL");
      long version = approvalService.get(expired).version();
      assertThatThrownBy(() -> inTransaction(() -> approvalService.confirm(expired, version)))
          .isInstanceOfSatisfying(
              ReservationExpiredException.class,
              e -> {
                assertThat(e.currentStatus()).isEqualTo(ReservationStatus.PENDING_APPROVAL);
                assertThat(e.currentVersion()).isEqualTo(version);
              });
      assertThat(inTransaction(() -> approvalService.reject(expired, version)).status())
          .isEqualTo(ReservationStatus.REJECTED);
    }

    @Test
    void equipmentThatBrokeWhileWaitingCannotBeConfirmed() {
      UUID item = pendingItem();
      var r = reservations.submit(borrower, request(item, 1, 3));
      fixtures.openMaintenanceCase(item, fixtures.user("BORROWER"));
      assertThatThrownBy(() -> inTransaction(() -> approvalService.confirm(r.reservationId(), r.version())))
          .isInstanceOf(ReservationStateConflictException.class)
          .hasMessageContaining("unavailable");
      assertThat(fixtures.reservationStatus(r.reservationId())).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void rejectionFreesTheSlotAndConfirmReturnsTheUpdatedView() {
      UUID item = pendingItem();
      var r = reservations.submit(borrower, request(item, 1, 3));
      assertThat(approvalService.get(r.reservationId()).requesterId()).isEqualTo(borrower);
      var snapshot = inTransaction(() -> approvalService.reject(r.reservationId(), r.version()));
      assertThat(snapshot.reservationId()).isEqualTo(r.reservationId());
      assertThat(fixtures.reservationStatus(r.reservationId())).isEqualTo("REJECTED");
      assertThat(reservations.submit(fixtures.user("BORROWER"), request(item, 1, 3)).status())
          .isEqualTo(ReservationStatus.PENDING_APPROVAL);
      var other = reservations.submit(fixtures.user("BORROWER"), request(item, 5, 6));
      assertThat(inTransaction(() -> approvalService.confirm(other.reservationId(), other.version())).requesterId())
          .isNotEqualTo(borrower);
    }
    @Test
    void approvalPortMapsTheViewAndTranslatesConflictsWithCurrentMetadata() {
      UUID item = pendingItem();
      var r = reservations.submit(borrower, request(item, 1, 3));
      var view = approvalPort.get(r.reservationId());
      assertThat(view.requesterId()).isEqualTo(borrower);
      assertThat(view.status()).isEqualTo("PENDING_APPROVAL");
      assertThat(view.equipment().equipmentId()).isEqualTo(item);
      assertThat(view.version()).isEqualTo(r.version());
      assertThat(approvalPort.findPendingApproval(PageRequest.of(0, 100)).getContent())
          .extracting(s -> s.reservationId())
          .contains(r.reservationId());

      assertThatThrownBy(() -> inTransaction(() -> approvalPort.confirm(r.reservationId(), r.version() + 1)))
          .isInstanceOfSatisfying(
              ApprovalConflictException.class,
              e -> {
                assertThat(e.kind()).isEqualTo(ApprovalConflictException.Kind.VERSION);
                assertThat(e.currentStatus()).isEqualTo("PENDING_APPROVAL");
                assertThat(e.currentVersion()).isEqualTo(r.version());
              });
      var rejected = inTransaction(() -> approvalPort.reject(r.reservationId(), r.version()));
      assertThat(rejected.status()).isEqualTo("REJECTED");
      assertThatThrownBy(() -> inTransaction(() -> approvalPort.confirm(r.reservationId(), rejected.version())))
          .isInstanceOfSatisfying(
              ApprovalConflictException.class,
              e -> {
                assertThat(e.kind()).isEqualTo(ApprovalConflictException.Kind.STATE);
                assertThat(e.currentStatus()).isEqualTo("REJECTED");
                assertThat(e.currentVersion()).isEqualTo(rejected.version());
              });
    }
  }

  static List<UUID> ids(List<EquipmentSummary> items) {
    return items.stream().map(EquipmentSummary::equipmentId).toList();
  }
}
