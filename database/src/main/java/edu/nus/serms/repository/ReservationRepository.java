package edu.nus.serms.repository;

import edu.nus.serms.domain.Equipment;
import edu.nus.serms.domain.Reservation;
import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Service layer must supply a verified authenticated user ID. */
public final class ReservationRepository {
    private final DataSource dataSource;
    public ReservationRepository(DataSource dataSource) { this.dataSource = dataSource; }

    /** Snapshot only. Search treats %, _ literally; book() arbitrates concurrent requests. */
    public List<Equipment> findAvailable(String query, Instant start, Instant end) throws SQLException {
        Reservation.validateWindow(start, end);
        String pattern = "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        String sql = """
            SELECT e.* FROM serms.equipment e
            WHERE e.status = 'AVAILABLE' AND ? >= statement_timestamp()
              AND (e.name ILIKE ? ESCAPE '!' OR e.asset_tag ILIKE ? ESCAPE '!'
                   OR e.category ILIKE ? ESCAPE '!')
              AND NOT EXISTS (SELECT 1 FROM serms.reservation r
                  WHERE r.equipment_id = e.id AND r.status IN ('PENDING', 'CONFIRMED')
                    AND tstzrange(r.starts_at, r.ends_at, '[)') && tstzrange(?, ?, '[)'))
            ORDER BY e.asset_tag LIMIT 100
            """;
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setTimestamp(1, Timestamp.from(start));
            for (int i = 2; i <= 4; i++) s.setString(i, pattern);
            s.setTimestamp(5, Timestamp.from(start));
            s.setTimestamp(6, Timestamp.from(end));
            List<Equipment> result = new ArrayList<>();
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) result.add(new Equipment(r.getObject("id", UUID.class),
                    r.getString("asset_tag"), r.getString("name"), r.getString("category"),
                    r.getString("location"), Equipment.Status.valueOf(r.getString("status")),
                    r.getBoolean("requires_approval")));
            }
            return List.copyOf(result);
        }
    }

    public Reservation book(UUID authenticatedUser, UUID equipment, Instant start, Instant end) throws SQLException {
        Reservation.validateWindow(start, end);
        UUID id = UUID.randomUUID();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                boolean approval;
                try (PreparedStatement s = c.prepareStatement(
                        "SELECT requires_approval FROM serms.equipment WHERE id = ? FOR UPDATE")) {
                    s.setObject(1, equipment);
                    try (ResultSet r = s.executeQuery()) {
                        if (!r.next()) throw new SQLException("Unknown equipment", "23503");
                        approval = r.getBoolean(1);
                    }
                }
                Reservation.Status status = approval ? Reservation.Status.PENDING : Reservation.Status.CONFIRMED;
                try (PreparedStatement s = c.prepareStatement("""
                        INSERT INTO serms.reservation(id, user_id, equipment_id, starts_at, ends_at, status)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """)) {
                    s.setObject(1, id); s.setObject(2, authenticatedUser); s.setObject(3, equipment);
                    s.setTimestamp(4, Timestamp.from(start)); s.setTimestamp(5, Timestamp.from(end));
                    s.setString(6, status.name()); s.executeUpdate();
                }
                c.commit();
                return new Reservation(id, authenticatedUser, equipment, start, end, status);
            } catch (SQLException | RuntimeException error) {
                try { c.rollback(); } catch (SQLException rollbackError) { error.addSuppressed(rollbackError); }
                throw error;
            }
        }
    }

    public boolean cancel(UUID authenticatedUser, UUID reservation) throws SQLException {
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement("""
                UPDATE serms.reservation SET status = 'CANCELLED'
                WHERE id = ? AND user_id = ? AND status IN ('PENDING', 'CONFIRMED')
                """)) {
            s.setObject(1, reservation); s.setObject(2, authenticatedUser);
            return s.executeUpdate() == 1;
        }
    }
}