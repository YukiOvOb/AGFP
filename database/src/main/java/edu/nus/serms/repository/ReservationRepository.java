package edu.nus.serms.repository;

import edu.nus.serms.domain.Equipment;
import edu.nus.serms.domain.Reservation;
import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Service layer supplies a verified actor and performs role authorization. Requires schema V002. */
public final class ReservationRepository {
    private final DataSource dataSource;
    public ReservationRepository(DataSource dataSource) { this.dataSource = dataSource; }

    /** Snapshot only; the write path locks and rechecks the same database predicate. */
    public List<Equipment> findAvailable(String query, Instant start, Instant end) throws SQLException {
        Reservation.validateWindow(start, end);
        String pattern = "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        String sql = """
            SELECT e.* FROM serms.equipment e
            WHERE serms.equipment_can_reserve(e.equipment_id, ?) AND ? > statement_timestamp()
              AND (e.name ILIKE ? ESCAPE '!' OR e.asset_tag ILIKE ? ESCAPE '!'
                   OR e.category ILIKE ? ESCAPE '!')
              AND NOT EXISTS (SELECT 1 FROM serms.reservation r
                  WHERE r.equipment_id = e.equipment_id
                    AND r.status IN ('PENDING_APPROVAL', 'CONFIRMED', 'FULFILLED')
                    AND tstzrange(r.start_at, r.end_at, '[)') && tstzrange(?, ?, '[)'))
            ORDER BY e.asset_tag LIMIT 100
            """;
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setTimestamp(1, Timestamp.from(start));
            s.setTimestamp(2, Timestamp.from(end));
            for (int i = 3; i <= 5; i++) s.setString(i, pattern);
            s.setTimestamp(6, Timestamp.from(start));
            s.setTimestamp(7, Timestamp.from(end));
            List<Equipment> result = new ArrayList<>();
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) result.add(new Equipment(r.getObject("equipment_id", UUID.class),
                    r.getString("asset_tag"), r.getString("name"), r.getString("category"),
                    r.getString("location"), Equipment.Status.valueOf(r.getString("status")),
                    r.getBoolean("requires_approval"), r.getInt("version")));
            }
            return List.copyOf(result);
        }
    }

    public Reservation book(UUID authenticatedUser, UUID equipment, Instant start, Instant end) throws SQLException {
        return book(authenticatedUser, equipment, start, end, null, UUID.randomUUID().toString());
    }

    public Reservation book(UUID authenticatedUser, UUID equipment, Instant start, Instant end,
                            String purpose, String requestId) throws SQLException {
        Reservation.validateWindow(start, end);
        UUID id = UUID.randomUUID();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                boolean approval;
                try (PreparedStatement s = c.prepareStatement(
                        "SELECT requires_approval FROM serms.equipment WHERE equipment_id = ? FOR UPDATE")) {
                    s.setObject(1, equipment);
                    try (ResultSet r = s.executeQuery()) {
                        if (!r.next()) throw new SQLException("Unknown equipment", "23503");
                        approval = r.getBoolean(1);
                    }
                }
                Reservation.Status status = approval ? Reservation.Status.PENDING_APPROVAL : Reservation.Status.CONFIRMED;
                try (PreparedStatement s = c.prepareStatement("""
                        INSERT INTO serms.reservation(reservation_id, requester_id, equipment_id, start_at, end_at, status, purpose)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    s.setObject(1, id); s.setObject(2, authenticatedUser); s.setObject(3, equipment);
                    s.setTimestamp(4, Timestamp.from(start)); s.setTimestamp(5, Timestamp.from(end));
                    s.setString(6, status.name()); s.setString(7, purpose); s.executeUpdate();
                }
                audit(c, authenticatedUser, id, "RESERVATION_CREATED", requestId);
                c.commit();
                return new Reservation(id, authenticatedUser, equipment, start, end, status, purpose, 0);
            } catch (SQLException | RuntimeException error) {
                rollback(c, error);
                throw error;
            }
        }
    }

    public boolean cancel(UUID authenticatedUser, UUID reservation) throws SQLException {
        return cancel(authenticatedUser, reservation, UUID.randomUUID().toString());
    }

    public boolean cancel(UUID authenticatedUser, UUID reservation, String requestId) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Consistent lock order: equipment before reservation. Query does not disclose owner data.
                try (PreparedStatement s = c.prepareStatement("""
                        SELECT e.equipment_id FROM serms.equipment e JOIN serms.reservation r USING (equipment_id)
                        WHERE r.reservation_id=? AND r.requester_id=? FOR UPDATE OF e
                        """)) {
                    s.setObject(1, reservation); s.setObject(2, authenticatedUser);
                    try (ResultSet r = s.executeQuery()) {
                        if (!r.next()) { c.rollback(); return false; }
                    }
                }
                try (PreparedStatement s = c.prepareStatement(
                        "SELECT account_status FROM serms.app_user WHERE user_id=? FOR SHARE")) {
                    s.setObject(1, authenticatedUser);
                    try (ResultSet r = s.executeQuery()) {
                        if (!r.next() || !"ACTIVE".equals(r.getString(1)))
                            throw new SQLException("Inactive user", "23514");
                    }
                }
                boolean changed;
                try (PreparedStatement s = c.prepareStatement("""
                        UPDATE serms.reservation SET status = 'CANCELLED'
                        WHERE reservation_id = ? AND requester_id = ? AND status IN ('PENDING_APPROVAL', 'CONFIRMED')
                        """)) {
                    s.setObject(1, reservation); s.setObject(2, authenticatedUser);
                    changed = s.executeUpdate() == 1;
                }
                if (changed) audit(c, authenticatedUser, reservation, "RESERVATION_CANCELLED", requestId);
                c.commit();
                return changed;
            } catch (SQLException | RuntimeException error) {
                rollback(c, error);
                throw error;
            }
        }
    }

    private static void audit(Connection c, UUID actor, UUID reservation, String action, String requestId) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("""
                INSERT INTO serms.audit_log(audit_id,actor_id,action,entity_type,entity_id,outcome,request_id)
                VALUES (?, ?, ?, 'Reservation', ?, 'SUCCESS', ?)
                """)) {
            s.setObject(1, UUID.randomUUID()); s.setObject(2, actor); s.setString(3, action);
            s.setObject(4, reservation); s.setString(5, requestId); s.executeUpdate();
        }
    }

    private static void rollback(Connection c, Exception error) {
        try { c.rollback(); } catch (SQLException rollbackError) { error.addSuppressed(rollbackError); }
    }
}
