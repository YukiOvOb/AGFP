package sg.edu.nus.serms.notification.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Resolves display names from persisted business data; events remain identity-only contracts. */
@Component
public class NotificationText {
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'SGT'")
      .withZone(ZoneId.of("Asia/Singapore"));
  private final JdbcTemplate jdbc;

  public NotificationText(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  public String time(Instant instant) { return TIME.format(instant); }

  public String equipment(UUID equipmentId) {
    return jdbc.queryForObject("SELECT name FROM serms.equipment WHERE equipment_id=?", String.class, equipmentId);
  }

  public String reservationEquipment(UUID reservationId) {
    return jdbc.queryForObject("""
        SELECT e.name FROM serms.reservation r JOIN serms.equipment e USING (equipment_id)
        WHERE r.reservation_id=?
        """, String.class, reservationId);
  }
}
