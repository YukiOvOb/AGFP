package sg.edu.nus.serms.equipment.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import sg.edu.nus.serms.equipment.domain.BusySlot;
import sg.edu.nus.serms.equipment.domain.EquipmentSearchCriteria;
import sg.edu.nus.serms.equipment.domain.EquipmentStatus;
import sg.edu.nus.serms.equipment.domain.EquipmentSummary;
import sg.edu.nus.serms.equipment.domain.SearchResult;
import sg.edu.nus.serms.equipment.domain.TimeWindow;

/**
 * Read-only queries over the V002 {@code serms} schema. The availability predicate is the same one
 * the reservation insert trigger uses ({@code serms.equipment_can_reserve}) plus the overlap test
 * of the {@code reservation_no_overlap} exclusion constraint, so "available" in search results
 * means a reservation for that window would be accepted at the time of the query.
 */
@Repository
public class EquipmentSearchRepository {

  /** Reservation states that hold a time slot (same set as {@code reservation_no_overlap}). */
  public static final String HOLDING_STATES = "('PENDING_APPROVAL','CONFIRMED','FULFILLED')";

  static final String AVAILABLE_PREDICATE =
      """
      (serms.equipment_can_reserve(e.equipment_id, :windowStart)
       AND NOT EXISTS (
         SELECT 1 FROM serms.reservation r
         WHERE r.equipment_id = e.equipment_id
           AND r.status IN """
          + HOLDING_STATES
          + """

           AND tstzrange(r.start_at, r.end_at, '[)') && tstzrange(:windowStart, :windowEnd, '[)')))
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public EquipmentSearchRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public SearchResult search(EquipmentSearchCriteria criteria) {
    var params = new MapSqlParameterSource();
    var where = new ArrayList<String>();
    where.add("e.status <> 'RETIRED'");
    if (criteria.keyword() != null) {
      where.add("(e.name ILIKE :keyword ESCAPE '\\' OR e.asset_tag ILIKE :keyword ESCAPE '\\')");
      params.addValue("keyword", "%" + escapeLike(criteria.keyword()) + "%");
    }
    if (criteria.category() != null) {
      where.add("e.category = :category");
      params.addValue("category", criteria.category());
    }
    if (criteria.location() != null) {
      where.add("e.location = :location");
      params.addValue("location", criteria.location());
    }
    String available = "NULL::boolean";
    Optional<TimeWindow> window = criteria.window();
    if (window.isPresent()) {
      params.addValue("windowStart", utc(window.get().start()));
      params.addValue("windowEnd", utc(window.get().end()));
      available = AVAILABLE_PREDICATE;
      if (criteria.availableOnly()) where.add(AVAILABLE_PREDICATE);
    }
    params.addValue("limit", criteria.size());
    params.addValue("offset", (long) criteria.page() * criteria.size());
    String sql =
        "SELECT e.equipment_id, e.asset_tag, e.name, e.category, e.location, e.status,"
            + " e.requires_approval, "
            + available
            + " AS available_in_window, count(*) OVER () AS total"
            + " FROM serms.equipment e WHERE "
            + String.join(" AND ", where)
            + " ORDER BY lower(e.name), e.asset_tag LIMIT :limit OFFSET :offset";
    long[] total = {0};
    List<EquipmentSummary> items =
        jdbc.query(
            sql,
            params,
            (rs, row) -> {
              total[0] = rs.getLong("total");
              return summary(rs);
            });
    if (items.isEmpty() && criteria.page() > 0) {
      total[0] = count(where, params);
    }
    return new SearchResult(items, total[0], criteria.page(), criteria.size());
  }

  public Optional<EquipmentSummary> findVisible(UUID equipmentId, TimeWindow window) {
    var params = new MapSqlParameterSource("id", equipmentId);
    String available = "NULL::boolean";
    if (window != null) {
      params.addValue("windowStart", utc(window.start()));
      params.addValue("windowEnd", utc(window.end()));
      available = AVAILABLE_PREDICATE;
    }
    return jdbc
        .query(
            "SELECT e.equipment_id, e.asset_tag, e.name, e.category, e.location, e.status,"
                + " e.requires_approval, "
                + available
                + " AS available_in_window FROM serms.equipment e"
                + " WHERE e.equipment_id = :id AND e.status <> 'RETIRED'",
            params,
            (rs, row) -> summary(rs))
        .stream()
        .findFirst();
  }

  /** Slots held by reservations and the open maintenance flag, inside {@code window}. */
  public List<BusySlot> busySlots(UUID equipmentId, TimeWindow window) {
    var params =
        new MapSqlParameterSource("id", equipmentId)
            .addValue("windowStart", utc(window.start()))
            .addValue("windowEnd", utc(window.end()));
    return jdbc.query(
        """
        SELECT r.start_at, r.end_at, r.status
        FROM serms.reservation r
        WHERE r.equipment_id = :id
          AND r.status IN """
            + HOLDING_STATES
            + """

          AND tstzrange(r.start_at, r.end_at, '[)') && tstzrange(:windowStart, :windowEnd, '[)')
        ORDER BY r.start_at
        """,
        params,
        (rs, row) ->
            new BusySlot(
                instant(rs, "start_at"),
                instant(rs, "end_at"),
                switch (rs.getString("status")) {
                  case "PENDING_APPROVAL" -> "PENDING_APPROVAL";
                  case "FULFILLED" -> "ON_LOAN";
                  default -> "RESERVED";
                }));
  }

  public boolean hasOpenMaintenance(UUID equipmentId) {
    Boolean open =
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM serms.maintenance_case m WHERE m.equipment_id = :id"
                + " AND m.status IN ('OPEN','ASSIGNED','IN_PROGRESS'))",
            new MapSqlParameterSource("id", equipmentId),
            Boolean.class);
    return Boolean.TRUE.equals(open);
  }

  public List<String> categories() {
    return jdbc.queryForList(
        "SELECT DISTINCT category FROM serms.equipment WHERE status <> 'RETIRED' ORDER BY category",
        new MapSqlParameterSource(),
        String.class);
  }

  public List<String> locations() {
    return jdbc.queryForList(
        "SELECT DISTINCT location FROM serms.equipment WHERE status <> 'RETIRED' ORDER BY location",
        new MapSqlParameterSource(),
        String.class);
  }

  private long count(List<String> where, MapSqlParameterSource params) {
    Long n =
        jdbc.queryForObject(
            "SELECT count(*) FROM serms.equipment e WHERE " + String.join(" AND ", where),
            params,
            Long.class);
    return n == null ? 0 : n;
  }

  private static EquipmentSummary summary(ResultSet rs) throws SQLException {
    Object available = rs.getObject("available_in_window");
    return new EquipmentSummary(
        rs.getObject("equipment_id", UUID.class),
        rs.getString("asset_tag"),
        rs.getString("name"),
        rs.getString("category"),
        rs.getString("location"),
        EquipmentStatus.valueOf(rs.getString("status")),
        rs.getBoolean("requires_approval"),
        available == null ? null : (Boolean) available);
  }

  static String escapeLike(String text) {
    return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  static OffsetDateTime utc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }

  static Instant instant(ResultSet rs, String column) throws SQLException {
    return rs.getObject(column, OffsetDateTime.class).toInstant();
  }
}
