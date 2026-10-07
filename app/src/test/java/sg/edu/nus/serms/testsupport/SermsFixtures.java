package sg.edu.nus.serms.testsupport;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Inserts the rows a UC01 test needs. Every call uses fresh random identifiers. */
public final class SermsFixtures {
  private final JdbcTemplate jdbc;

  public SermsFixtures(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public UUID user(String role) {
    return user(role, "ACTIVE");
  }

  public UUID user(String role, String accountStatus) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO serms.app_user(user_id, email, display_name, password_hash, account_status)"
            + " VALUES (?, ?, 'UC01 test user', 'TEST_ONLY_NOT_A_LOGIN_HASH', ?)",
        id,
        id + "@example.invalid",
        accountStatus);
    jdbc.update(
        "INSERT INTO serms.user_role(user_id, role_id)"
            + " SELECT ?, role_id FROM serms.role WHERE code = ?",
        id,
        role);
    return id;
  }

  public UUID equipment(String name, String category, String location, boolean requiresApproval) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO serms.equipment(equipment_id, asset_tag, name, category, location,"
            + " requires_approval) VALUES (?, ?, ?, ?, ?, ?)",
        id,
        "UC01-" + id,
        name,
        category,
        location,
        requiresApproval);
    return id;
  }

  public void equipmentStatus(UUID equipmentId, String status) {
    jdbc.update("UPDATE serms.equipment SET status = ? WHERE equipment_id = ?", status, equipmentId);
  }

  public void openMaintenanceCase(UUID equipmentId, UUID reporter) {
    jdbc.update(
        "INSERT INTO serms.maintenance_case(maintenance_case_id, equipment_id, reported_by,"
            + " fault_description) VALUES (?, ?, ?, 'Lens cracked')",
        UUID.randomUUID(),
        equipmentId,
        reporter);
  }

  /** Inserts a reservation row directly (bypassing the service), e.g. someone else's booking. */
  public UUID reservation(UUID requester, UUID equipment, Instant start, Instant end, String status) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO serms.reservation(reservation_id, requester_id, equipment_id, start_at,"
            + " end_at, status) VALUES (?, ?, ?, ?, ?, ?)",
        id,
        requester,
        equipment,
        utc(start),
        utc(end),
        status);
    return id;
  }

  /** Checks out a confirmed reservation the way the loan module will: loan row + FULFILLED. */
  public void checkOut(UUID reservationId, UUID custodian) {
    jdbc.update(
        "INSERT INTO serms.loan(loan_id, reservation_id, checkout_by, checked_out_at, due_at)"
            + " SELECT ?, reservation_id, ?, now(), end_at FROM serms.reservation"
            + " WHERE reservation_id = ?",
        UUID.randomUUID(),
        custodian,
        reservationId);
    jdbc.update(
        "UPDATE serms.reservation SET status = 'FULFILLED' WHERE reservation_id = ?",
        reservationId);
  }

  public String reservationStatus(UUID reservationId) {
    return jdbc.queryForObject(
        "SELECT status FROM serms.reservation WHERE reservation_id = ?", String.class, reservationId);
  }

  public static OffsetDateTime utc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }
}
