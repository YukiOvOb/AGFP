package sg.edu.nus.serms.report.repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import sg.edu.nus.serms.report.domain.OperationsReport.CategoryOverdue;
import sg.edu.nus.serms.report.domain.OperationsReport.CategoryReservations;
import sg.edu.nus.serms.report.domain.OperationsReport.EquipmentMaintenance;
import sg.edu.nus.serms.report.domain.OperationsReport.OverdueLoan;

/**
 * Read-only aggregation over the shared V002 tables. No business decisions: the service turns
 * these rows into ratios and totals.
 */
@Repository
public class ReportRepository {

  /** Raw loaned time of one item inside a window; the service computes the ratio. */
  public record LoanedTime(
      UUID equipmentId,
      String assetTag,
      String name,
      String category,
      String status,
      long loans,
      Duration loanedTime) {}

  private final NamedParameterJdbcTemplate jdbc;

  public ReportRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Reservations whose slot starts in [from, to), by category and status. */
  public List<CategoryReservations> reservationsByCategory(Instant from, Instant to) {
    return jdbc.query(
        """
        SELECT e.category,
               count(*) AS total,
               count(*) FILTER (WHERE r.status = 'PENDING_APPROVAL') AS pending_approval,
               count(*) FILTER (WHERE r.status = 'CONFIRMED') AS confirmed,
               count(*) FILTER (WHERE r.status = 'FULFILLED') AS fulfilled,
               count(*) FILTER (WHERE r.status = 'REJECTED') AS rejected,
               count(*) FILTER (WHERE r.status = 'CANCELLED') AS cancelled
        FROM serms.reservation r JOIN serms.equipment e ON e.equipment_id = r.equipment_id
        WHERE r.start_at >= :from AND r.start_at < :to
        GROUP BY e.category
        ORDER BY total DESC, e.category
        """,
        window(from, to),
        (rs, row) ->
            new CategoryReservations(
                rs.getString("category"),
                rs.getLong("total"),
                rs.getLong("pending_approval"),
                rs.getLong("confirmed"),
                rs.getLong("fulfilled"),
                rs.getLong("rejected"),
                rs.getLong("cancelled")));
  }

  /**
   * Time each item spent on loan inside [from, until), counting a loan that is still out up to
   * {@code until}. Retired items appear only when they were on loan in the window.
   */
  public List<LoanedTime> loanedTime(Instant from, Instant until) {
    return jdbc.query(
        """
        SELECT e.equipment_id, e.asset_tag, e.name, e.category, e.status,
               count(l.loan_id) AS loans,
               -- LEAST and GREATEST skip NULLs, so rows without a loan must be filtered out.
               COALESCE(sum(EXTRACT(EPOCH FROM
                   LEAST(COALESCE(l.returned_at, :until), :until) - GREATEST(l.checked_out_at, :from)))
                   FILTER (WHERE l.loan_id IS NOT NULL),
                 0) AS loaned_seconds
        FROM serms.equipment e
        LEFT JOIN serms.reservation r ON r.equipment_id = e.equipment_id
        LEFT JOIN serms.loan l ON l.reservation_id = r.reservation_id
             AND l.checked_out_at < :until AND COALESCE(l.returned_at, :until) > :from
        WHERE e.status <> 'RETIRED' OR l.loan_id IS NOT NULL
        GROUP BY e.equipment_id, e.asset_tag, e.name, e.category, e.status
        ORDER BY loaned_seconds DESC, e.name, e.equipment_id
        """,
        new MapSqlParameterSource("from", utc(from)).addValue("until", utc(until)),
        (rs, row) ->
            new LoanedTime(
                rs.getObject("equipment_id", UUID.class),
                rs.getString("asset_tag"),
                rs.getString("name"),
                rs.getString("category"),
                rs.getString("status"),
                rs.getLong("loans"),
                seconds(rs.getBigDecimal("loaned_seconds"))));
  }

  /** Loans still out after their due time, longest overdue first. */
  public List<OverdueLoan> overdueAt(Instant now) {
    return jdbc.query(
        """
        SELECT l.loan_id, e.equipment_id, e.asset_tag, e.name, e.category,
               u.user_id, u.display_name, l.due_at
        FROM serms.loan l
        JOIN serms.reservation r ON r.reservation_id = l.reservation_id
        JOIN serms.equipment e ON e.equipment_id = r.equipment_id
        JOIN serms.app_user u ON u.user_id = r.requester_id
        WHERE l.status = 'ACTIVE' AND l.due_at < :now
        ORDER BY l.due_at, l.loan_id
        """,
        new MapSqlParameterSource("now", utc(now)),
        (rs, row) -> {
          Instant due = rs.getObject("due_at", OffsetDateTime.class).toInstant();
          return new OverdueLoan(
              rs.getObject("loan_id", UUID.class),
              rs.getObject("equipment_id", UUID.class),
              rs.getString("asset_tag"),
              rs.getString("name"),
              rs.getString("category"),
              rs.getObject("user_id", UUID.class),
              rs.getString("display_name"),
              due,
              Duration.between(due, now));
        });
  }

  /**
   * Loans that fell due in [from, until), by category: returned after their due time, or not
   * returned at all. {@code until} must not be later than now, so an active loan here is overdue.
   */
  public List<CategoryOverdue> overdueByCategory(Instant from, Instant until) {
    return jdbc.query(
        """
        SELECT e.category,
               count(*) AS loans_due,
               count(*) FILTER (WHERE l.returned_at > l.due_at) AS returned_late,
               count(*) FILTER (WHERE l.status = 'ACTIVE') AS still_overdue
        FROM serms.loan l
        JOIN serms.reservation r ON r.reservation_id = l.reservation_id
        JOIN serms.equipment e ON e.equipment_id = r.equipment_id
        WHERE l.due_at >= :from AND l.due_at < :until
        GROUP BY e.category
        ORDER BY e.category
        """,
        new MapSqlParameterSource("from", utc(from)).addValue("until", utc(until)),
        (rs, row) ->
            new CategoryOverdue(
                rs.getString("category"),
                rs.getLong("loans_due"),
                rs.getLong("returned_late"),
                rs.getLong("still_overdue")));
  }

  /** Maintenance cases reported in [from, to), per item, most first. */
  public List<EquipmentMaintenance> maintenanceByEquipment(Instant from, Instant to, int limit) {
    return jdbc.query(
        """
        SELECT e.equipment_id, e.asset_tag, e.name, e.category, count(*) AS cases
        FROM serms.maintenance_case m JOIN serms.equipment e ON e.equipment_id = m.equipment_id
        WHERE m.reported_at >= :from AND m.reported_at < :to
        GROUP BY e.equipment_id, e.asset_tag, e.name, e.category
        ORDER BY cases DESC, e.name, e.equipment_id
        LIMIT :limit
        """,
        window(from, to).addValue("limit", limit),
        (rs, row) ->
            new EquipmentMaintenance(
                rs.getObject("equipment_id", UUID.class),
                rs.getString("asset_tag"),
                rs.getString("name"),
                rs.getString("category"),
                rs.getLong("cases")));
  }

  private static MapSqlParameterSource window(Instant from, Instant to) {
    return new MapSqlParameterSource("from", utc(from)).addValue("to", utc(to));
  }

  private static OffsetDateTime utc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }

  /** PostgreSQL returns numeric seconds; keep millisecond precision. */
  private static Duration seconds(BigDecimal value) {
    if (value == null || value.signum() <= 0) return Duration.ZERO;
    return Duration.ofMillis(value.movePointRight(3).setScale(0, RoundingMode.HALF_UP).longValueExact());
  }
}
