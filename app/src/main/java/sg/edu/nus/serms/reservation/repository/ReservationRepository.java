package sg.edu.nus.serms.reservation.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import sg.edu.nus.serms.reservation.domain.PendingReservation;
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
  /** {@code maxBorrowDuration} is null when the global reservation limit applies (V005). */
  public record LockedEquipment(
      UUID equipmentId, String status, boolean requiresApproval, Duration maxBorrowDuration) {}

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

  private static Duration seconds(long value, boolean isNull) {
    return isNull ? null : Duration.ofSeconds(value);
  }

  public Optional<LockedEquipment> lockEquipment(UUID equipmentId) {
    return jdbc
        .query(
            "SELECT equipment_id, status, requires_approval,"
                + " EXTRACT(EPOCH FROM max_borrow_duration)::bigint AS max_borrow_seconds"
                + " FROM serms.equipment WHERE equipment_id = :id FOR UPDATE",
            new MapSqlParameterSource("id", equipmentId),
            (rs, row) ->
                new LockedEquipment(
                    rs.getObject("equipment_id", UUID.class),
                    rs.getString("status"),
                    rs.getBoolean("requires_approval"),
                    seconds(rs.getLong("max_borrow_seconds"), rs.wasNull())))
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

  /** True when the requester still holds equipment whose loan is past its due time. */
  public boolean hasOverdueLoan(UUID requesterId, Instant now) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM serms.loan l"
                + " JOIN serms.reservation r ON r.reservation_id = l.reservation_id"
                + " WHERE r.requester_id = :id AND l.status = 'ACTIVE' AND l.due_at < :now)",
            new MapSqlParameterSource("id", requesterId).addValue("now", utc(now)),
            Boolean.class));
  }

  /**
   * Appends an audit record (Proposal 5.7, business rule 9). Called inside the booking
   * transaction, so a rolled-back booking leaves no record; {@code serms.audit_log} rejects
   * updates and deletes.
   */
  public void audit(UUID actorId, String action, UUID reservationId, String summary, Instant at) {
    UUID auditId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO serms.audit_log
          (audit_id, actor_id, action, entity_type, entity_id, outcome, request_id,
           change_summary, occurred_at)
        VALUES (:id, :actor, :action, 'Reservation', :entity, 'SUCCESS', :request, :summary, :at)
        """,
        new MapSqlParameterSource("id", auditId)
            .addValue("actor", actorId)
            .addValue("action", action)
            .addValue("entity", reservationId)
            .addValue("request", "reservation:" + auditId)
            .addValue("summary", summary)
            .addValue("at", utc(at)));
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

  private static final String PENDING_SELECT =
      """
      SELECT r.reservation_id, r.requester_id, u.display_name, r.equipment_id, e.asset_tag, e.name,
             e.category, r.start_at, r.end_at, r.purpose, r.status, r.version, r.created_at,
             (r.status = 'PENDING_APPROVAL' AND r.start_at <= :now) AS expired
      FROM serms.reservation r
      JOIN serms.equipment e ON e.equipment_id = r.equipment_id
      JOIN serms.app_user u ON u.user_id = r.requester_id
      """;

  /** Pending requests, earliest start first; includes expired ones (UC02 list). */
  public List<PendingReservation> findPendingApproval(Instant now, int limit, long offset) {
    return jdbc.query(
        PENDING_SELECT
            + " WHERE r.status = 'PENDING_APPROVAL'"
            + " ORDER BY r.start_at, r.created_at, r.reservation_id LIMIT :limit OFFSET :offset",
        new MapSqlParameterSource("now", utc(now)).addValue("limit", limit).addValue("offset", offset),
        (rs, row) -> pending(rs));
  }

  public long countPendingApproval() {
    Long n =
        jdbc.queryForObject(
            "SELECT count(*) FROM serms.reservation WHERE status = 'PENDING_APPROVAL'",
            new MapSqlParameterSource(),
            Long.class);
    return n == null ? 0 : n;
  }

  public Optional<PendingReservation> findPendingView(UUID reservationId, Instant now) {
    return jdbc
        .query(
            PENDING_SELECT + " WHERE r.reservation_id = :id",
            new MapSqlParameterSource("id", reservationId).addValue("now", utc(now)),
            (rs, row) -> pending(rs))
        .stream()
        .findFirst();
  }

  public Optional<UUID> equipmentIdOf(UUID reservationId) {
    return jdbc
        .queryForList(
            "SELECT equipment_id FROM serms.reservation WHERE reservation_id = :id",
            new MapSqlParameterSource("id", reservationId),
            UUID.class)
        .stream()
        .findFirst();
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

  private static PendingReservation pending(ResultSet rs) throws SQLException {
    return new PendingReservation(
        rs.getObject("reservation_id", UUID.class),
        rs.getObject("requester_id", UUID.class),
        rs.getString("display_name"),
        new PendingReservation.EquipmentSummary(
            rs.getObject("equipment_id", UUID.class),
            rs.getString("asset_tag"),
            rs.getString("name"),
            rs.getString("category")),
        instant(rs, "start_at"),
        instant(rs, "end_at"),
        rs.getString("purpose"),
        ReservationStatus.valueOf(rs.getString("status")),
        rs.getLong("version"),
        instant(rs, "created_at"),
        rs.getBoolean("expired"));
  }

  private static OffsetDateTime utc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    return rs.getObject(column, OffsetDateTime.class).toInstant();
  }
}
