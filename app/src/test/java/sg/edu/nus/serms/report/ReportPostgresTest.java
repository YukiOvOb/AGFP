package sg.edu.nus.serms.report;

import static org.assertj.core.api.Assertions.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import sg.edu.nus.serms.maintenance.repository.jdbc.JdbcMaintenanceReportRepository;
import sg.edu.nus.serms.maintenance.service.MaintenanceReportService;
import sg.edu.nus.serms.report.domain.OperationsReport.CategoryOverdue;
import sg.edu.nus.serms.report.domain.OperationsReport.CategoryReservations;
import sg.edu.nus.serms.report.repository.ReportRepository;
import sg.edu.nus.serms.report.repository.ReportRepository.LoanedTime;
import sg.edu.nus.serms.report.service.ReportService;
import sg.edu.nus.serms.testsupport.SermsFixtures;
import sg.edu.nus.serms.testsupport.SermsPostgres;

/**
 * Report aggregation against real PostgreSQL 18 with the V001 + V002 schema. The database is
 * shared with other tests, so every test uses its own category and looks only at its own rows.
 * The schema refuses loans that are already overdue, so tests pass a later "now" instead.
 */
class ReportPostgresTest {
  static final Duration TOLERANCE = Duration.ofSeconds(10);
  final JdbcTemplate jdbc = new JdbcTemplate(SermsPostgres.dataSource());
  final ReportRepository reports =
      new ReportRepository(new NamedParameterJdbcTemplate(SermsPostgres.dataSource()));
  SermsFixtures fixtures;
  String category;
  UUID borrower;
  UUID custodian;

  @BeforeEach
  void setUp() {
    fixtures = new SermsFixtures(jdbc);
    category = "Report-" + UUID.randomUUID();
    borrower = fixtures.user("BORROWER");
    custodian = fixtures.user("CUSTODIAN");
  }

  UUID item(String name, boolean requiresApproval) {
    return fixtures.equipment(name, category, "Report Lab", requiresApproval);
  }

  /** Checked out now, due {@code dueIn} from now. */
  UUID loan(UUID equipment, Duration dueIn) {
    UUID reservation =
        fixtures.reservation(borrower, equipment, Instant.now().plusSeconds(30), Instant.now().plus(dueIn), "CONFIRMED");
    fixtures.checkOut(reservation, custodian);
    return reservation;
  }

  /** Records the return {@code after} the checkout time. */
  void returned(UUID reservation, String after) {
    jdbc.update(
        "UPDATE serms.loan SET status = 'RETURNED', return_by = ?, returned_at = checked_out_at + CAST(? AS interval),"
            + " return_condition = 'GOOD' WHERE reservation_id = ?",
        custodian,
        after,
        reservation);
  }

  LoanedTime mine(List<LoanedTime> rows, UUID equipment) {
    return rows.stream().filter(r -> r.equipmentId().equals(equipment)).findFirst().orElse(null);
  }

  @Test
  void countsReservationsStartingInThePeriodByCategoryAndStatus() {
    UUID camera = item("Camera", false);
    UUID tripod = item("Tripod", true);
    Instant start = Instant.now().truncatedTo(ChronoUnit.HOURS).plus(Duration.ofDays(1));
    fixtures.reservation(borrower, camera, start, start.plusSeconds(3600), "CONFIRMED");
    UUID cancelled = fixtures.reservation(borrower, camera, start.plusSeconds(7200), start.plusSeconds(10800), "CONFIRMED");
    jdbc.update("UPDATE serms.reservation SET status = 'CANCELLED' WHERE reservation_id = ?", cancelled);
    fixtures.reservation(borrower, tripod, start, start.plusSeconds(3600), "PENDING_APPROVAL");
    UUID rejected =
        fixtures.reservation(borrower, tripod, start.plusSeconds(7200), start.plusSeconds(10800), "PENDING_APPROVAL");
    jdbc.update("UPDATE serms.reservation SET status = 'REJECTED' WHERE reservation_id = ?", rejected);
    // Starts after the period: not counted.
    fixtures.reservation(borrower, camera, start.plus(Duration.ofDays(5)), start.plus(Duration.ofDays(5)).plusSeconds(3600), "CONFIRMED");

    var rows = reports.reservationsByCategory(start.minusSeconds(3600), start.plus(Duration.ofDays(1)));
    assertThat(rows)
        .filteredOn(r -> r.category().equals(category))
        .singleElement()
        .isEqualTo(new CategoryReservations(category, 4, 1, 1, 0, 1, 1));
  }

  @Test
  void measuresLoanedTimeInsideTheWindowOnly() {
    UUID out = item("Projector", false);
    UUID back = item("Laptop", false);
    UUID idle = item("Microphone", false);
    UUID retired = item("Old scanner", false);
    fixtures.equipmentStatus(retired, "RETIRED");
    loan(out, Duration.ofHours(3));
    returned(loan(back, Duration.ofHours(2)), "30 minutes");

    Instant now = Instant.now();
    var rows = reports.loanedTime(now.minus(Duration.ofHours(1)), now.plus(Duration.ofHours(1)));
    // Still out: counted from checkout to the end of the window.
    assertThat(mine(rows, out).loanedTime()).isCloseTo(Duration.ofHours(1), TOLERANCE);
    assertThat(mine(rows, out).loans()).isOne();
    assertThat(mine(rows, back).loanedTime()).isCloseTo(Duration.ofMinutes(30), TOLERANCE);
    assertThat(mine(rows, idle).loanedTime()).isZero();
    assertThat(mine(rows, idle).loans()).isZero();
    assertThat(mine(rows, retired)).as("retired and never on loan in the window").isNull();
    // A window that ends before the checkout sees none of it.
    var before = reports.loanedTime(now.minus(Duration.ofHours(3)), now.minus(Duration.ofHours(2)));
    assertThat(mine(before, out).loanedTime()).isZero();
  }

  @Test
  void listsLoansThatAreOverdueAtTheGivenTime() {
    UUID late = item("Camera", false);
    UUID back = item("Tripod", false);
    UUID lateLoan = loan(late, Duration.ofHours(3));
    returned(loan(back, Duration.ofHours(1)), "10 minutes");
    UUID loanId =
        jdbc.queryForObject("SELECT loan_id FROM serms.loan WHERE reservation_id = ?", UUID.class, lateLoan);

    Instant later = Instant.now().plus(Duration.ofHours(4));
    var overdue = reports.overdueAt(later).stream().filter(o -> o.category().equals(category)).toList();
    assertThat(overdue).singleElement().satisfies(o -> {
      assertThat(o.loanId()).isEqualTo(loanId);
      assertThat(o.equipmentId()).isEqualTo(late);
      assertThat(o.borrowerId()).isEqualTo(borrower);
      assertThat(o.overdueBy()).isCloseTo(Duration.ofHours(1), TOLERANCE);
    });
    assertThat(reports.overdueAt(Instant.now()).stream().filter(o -> o.category().equals(category))).isEmpty();
  }

  @Test
  void countsLateReturnsAndLoansStillOut() {
    returned(loan(item("Camera", false), Duration.ofHours(1)), "2 hours"); // back an hour late
    returned(loan(item("Tripod", false), Duration.ofHours(2)), "30 minutes"); // back early
    loan(item("Laptop", false), Duration.ofHours(3)); // still out
    loan(item("Drone", false), Duration.ofHours(8)); // falls due after the window

    Instant now = Instant.now();
    var rows = reports.overdueByCategory(now, now.plus(Duration.ofHours(5)));
    assertThat(rows)
        .filteredOn(r -> r.category().equals(category))
        .singleElement()
        .isEqualTo(new CategoryOverdue(category, 3, 1, 1));
  }

  @Test
  void ranksEquipmentByCasesReportedInThePeriod() {
    UUID fragile = item("Fragile lens", false);
    UUID sturdy = item("Sturdy stand", false);
    fixtures.openMaintenanceCase(fragile, borrower);
    fixtures.openMaintenanceCase(fragile, custodian);
    fixtures.openMaintenanceCase(sturdy, borrower);
    Instant now = Instant.now();
    var rows =
        reports.maintenanceByEquipment(now.minus(Duration.ofHours(1)), now.plus(Duration.ofHours(1)), 500).stream()
            .filter(r -> r.category().equals(category))
            .toList();
    assertThat(rows).extracting(r -> r.equipmentId(), r -> r.cases())
        .containsExactly(tuple(fragile, 2L), tuple(sturdy, 1L));
    assertThat(reports.maintenanceByEquipment(now.minus(Duration.ofHours(1)), now.plus(Duration.ofHours(1)), 1))
        .hasSize(1);
  }

  @Test
  void theServiceCombinesEverySectionForOnePeriod() {
    UUID camera = item("Camera", false);
    loan(camera, Duration.ofHours(2));
    fixtures.openMaintenanceCase(item("Tripod", false), borrower);
    Instant now = Instant.now();
    var service =
        new ReportService(
            reports,
            new MaintenanceReportService(new JdbcMaintenanceReportRepository(jdbc)),
            Clock.fixed(now.plus(Duration.ofHours(1)), ZoneOffset.UTC));
    var report = service.report(now.minus(Duration.ofHours(1)), now.plus(Duration.ofDays(1)));
    assertThat(report.measuredUntil()).isEqualTo(now.plus(Duration.ofHours(1)));
    var cameraRow =
        report.equipmentUtilisation().stream().filter(e -> e.equipmentId().equals(camera)).findFirst().orElseThrow();
    // On loan for about one of the two measured hours.
    assertThat(cameraRow.utilisation()).isCloseTo(0.5, within(0.01));
    assertThat(report.utilisationByCategory()).anySatisfy(c -> assertThat(c.category()).isEqualTo(category));
    assertThat(report.maintenance().casesByStatus()).containsKeys("OPEN", "ASSIGNED", "IN_PROGRESS", "RESOLVED", "UNREPAIRABLE");
    assertThat(report.maintenance().reportedInPeriod()).isPositive();
    assertThat(report.maintenanceByEquipment()).isNotEmpty();
  }
}
