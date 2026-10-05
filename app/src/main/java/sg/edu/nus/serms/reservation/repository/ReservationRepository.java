package sg.edu.nus.serms.reservation.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import sg.edu.nus.serms.reservation.domain.Reservation;
import sg.edu.nus.serms.reservation.domain.ReservationStatus;

/**
 * JDBC access to {@code serms.reservation} (V002). Writers lock the equipment row first and the
 * requester second, the same order the {@code reservation_rules} trigger uses, so concurrent
 * bookings queue instead of deadlocking.
 */
@Repository
public class ReservationRepository {

  /** Equipment facts read under {@code FOR UPDATE} at the start of a booking. */
  public record LockedEquipment(UUID equipmentId, String status, boolean requiresApproval) {}

  public record Page(List<Reservation> items, long totalElements) {}

  private static final String SELECT =
      """
      SELECT r.reservation_id, r.requester_id, r.equipment_id, e.asset_tag, e.name, e.location,
             r.start_at, r.end_at, r.status, r.purpose, r.created_at, r.version,
             EXISTS (SELECT 1 FROM serms.loan l WHERE l.reservation_id = r.reservation_id)
               AS checked_out
      FROM serms.reservation r JOIN serms.equipment e ON e.equipment_id = r.equipment_id
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public ReservationRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<LockedEquipment> lockEquipment(UUID equipmentId) {
    return jdbc
        .query(
            "SELECT equipment_id, status, requires_approval FROM serms.equipment"
                + " WHERE equipment_id = :id FOR UPDATE",
            new MapSqlParameterSource("id", equipmentId),
            (rs, row) ->
                new LockedEquipment(
                    rs.getObject("equipment_id", UUID.class),
                    rs.getString("status"),
                    rs.getBoolean("requires_approval")))
        .stream()
        .findFirst();
  }

  /** Locks the requester so two parallel bookings by one person cannot both pass the quota. */
  public Optional<String> lockRequesterAccountStatus(UUID requesterId) {
    return jdbc
        .queryForList(
            "SELECT account_status FROM serms.app_user WHERE user_id = :id FOR UPDATE",
            new MapSqlParameterSource("id", requesterId),
            String.class)
        .stream()
        .findFirst();
  }

  /** Same predicate the insert trigger applies ({@code serms.equipment_can_reserve}). */
  public boolean equipmentCanBeReserved(UUID equipmentId, Instant startAt) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT serms.equipment_can_reserve(:id, :start)",
            new MapSqlParameterSource("id", equipmentId).addValue("start", utc(startAt)),
            Boolean.class));
  }

  public boolean overlapsHeldSlot(UUID equipmentId, Instant startAt, Instant endAt) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            """
            SELECT EXISTS (SELECT 1 FROM serms.reservation r
              WHERE r.equipment_id = :id
                AND r.status IN ('PENDING_APPROVAL','CONFIRMED','FULFILLED')
                AND tstzrange(r.start_at, r.end_at, '[)') && tstzrange(:start, :end, '[)'))
            """,
            new MapSqlParameterSource("id", equipmentId)
                .addValue("start", utc(startAt))
                .addValue("end", utc(endAt)),
            Boolean.class));
  }

  public boolean hasOpenMaintenance(UUID equipmentId) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM serms.maintenance_case m WHERE m.equipment_id = :id"
                + " AND m.status IN ('OPEN','ASSIGNED','IN_PROGRESS'))",
            new MapSqlParameterSource("id", equipmentId),
            Boolean.class));
  }

  /** Open reservations that have not yet ended; these count towards the quota. */
  public int countOpen(UUID requesterId, Instant now) {
    Integer n =
        jdbc.queryForObject(
            "SELECT count(*) FROM serms.reservation WHERE requester_id = :id"
                + " AND status IN ('PENDING_APPROVAL','CONFIRMED') AND end_at > :now",
            new MapSqlParameterSource("id", requesterId).addValue("now", utc(now)),
            Integer.class);
    return n == null ? 0 : n;
  }

  public void insert(
      UUID reservationId,
      UUID requesterId,
      UUID equipmentId,
      Instant startAt,
      Instant endAt,
      ReservationStatus status,
      String purpose) {
    jdbc.update(
        """
        INSERT INTO serms.reservation
          (reservation_id, requester_id, equipment_id, start_at, end_at, status, purpose)
        VALUES (:id, :requester, :equipment, :start, :end, :status, :purpose)
        """,
        new MapSqlParameterSource("id", reservationId)
            .addValue("requester", requesterId)
            .addValue("equipment", equipmentId)
            .addValue("start", utc(startAt))
            .addValue("end", utc(endAt))
            .addValue("status", status.name())
            .addValue("purpose", purpose));
  }

  public Optional<Reservation> findById(UUID reservationId) {
    return jdbc
        .query(
            SELECT + " WHERE r.reservation_id = :id",
            new MapSqlParameterSource("id", reservationId),
            (rs, row) -> map(rs))
        .stream()
        .findFirst();
  }

  /** Row lock on the reservation, taken before a status change. */
  public Optional<Reservation> findByIdForUpdate(UUID reservationId) {
    return jdbc
        .query(
            SELECT + " WHERE r.reservation_id = :id FOR UPDATE OF r",
            new MapSqlParameterSource("id", reservationId),
            (rs, row) -> map(rs))
        .stream()
        .findFirst();
  }

  public Page findByRequester(UUID requesterId, ReservationStatus status, int page, int size) {
    var params =
        new MapSqlParameterSource("requester", requesterId)
            .addValue("limit", size)
            .addValue("offset", (long) page * size);
    String filter = "";
    if (status != null) {
      filter = " AND r.status = :status";
      params.addValue("status", status.name());
    }
    List<Reservation> items =
        jdbc.query(
            SELECT
                + " WHERE r.requester_id = :requester"
                + filter
                + " ORDER BY r.start_at DESC, r.reservation_id LIMIT :limit OFFSET :offset",
            params,
            (rs, row) -> map(rs));
    Long total =
        jdbc.queryForObject(
            "SELECT count(*) FROM serms.reservation r WHERE r.requester_id = :requester" + filter,
            params,
            Long.class);
    return new Page(items, total == null ? 0 : total);
  }

  /**
   * Compare-and-set status change. Returns false when the row is no longer in {@code from} or its
   * version moved on; the database trigger still validates the transition itself.
   */
  public boolean changeStatus(
      UUID reservationId, ReservationStatus from, ReservationStatus to, Long expectedVersion) {
    var params =
        new MapSqlParameterSource("id", reservationId)
            .addValue("from", from.name())
            .addValue("to", to.name());
    String versionCheck = "";
    if (expectedVersion != null) {
      versionCheck = " AND version = :version";
      params.addValue("version", expectedVersion);
    }
    return jdbc.update(
            "UPDATE serms.reservation SET status = :to WHERE reservation_id = :id"
                + " AND status = :from"
                + versionCheck,
            params)
        == 1;
  }

  private static Reservation map(ResultSet rs) throws SQLException {
    return new Reservation(
        rs.getObject("reservation_id", UUID.class),
        rs.getObject("requester_id", UUID.class),
        rs.getObject("equipment_id", UUID.class),
        rs.getString("asset_tag"),
        rs.getString("name"),
        rs.getString("location"),
        instant(rs, "start_at"),
        instant(rs, "end_at"),
        ReservationStatus.valueOf(rs.getString("status")),
        rs.getString("purpose"),
        instant(rs, "created_at"),
        rs.getLong("version"),
        rs.getBoolean("checked_out"));
  }

  private static OffsetDateTime utc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    return rs.getObject(column, OffsetDateTime.class).toInstant();
  }
}
