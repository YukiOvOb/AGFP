package sg.edu.nus.serms.equipment.service;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.serms.equipment.api.EquipmentManagementController.EquipmentInput;
import sg.edu.nus.serms.shared.domain.RecordNotFoundException;
import sg.edu.nus.serms.shared.security.SermsUserPrincipal;

@Service
@Transactional
public class EquipmentManagementService {
  public record ManagedEquipment(UUID equipmentId, String assetTag, String serialNumber,
      String name, String description, String category, String location, String condition,
      String status, boolean requiresApproval, String maxBorrowDuration, long version) {}

  private static final String COLUMNS = "equipment_id,asset_tag,serial_number,name,description,category,location,condition,status,requires_approval,EXTRACT(EPOCH FROM max_borrow_duration)::bigint AS max_seconds,version";
  private final JdbcTemplate jdbc;
  public EquipmentManagementService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAnyRole('CUSTODIAN','ADMIN')")
  public List<ManagedEquipment> list(int page, int size) {
    actor();
    if (page < 0 || page > 100000 || size < 1 || size > 100)
      throw new EquipmentManagementException(400, "page", "Choose a valid page and size.");
    return jdbc.query("SELECT " + COLUMNS + " FROM serms.equipment ORDER BY lower(name),asset_tag LIMIT ? OFFSET ?",
        (rs, row) -> map(rs), size, (long) page * size);
  }

  @PreAuthorize("hasAnyRole('CUSTODIAN','ADMIN')")
  public ManagedEquipment register(EquipmentInput input) {
    UUID actor = actor();
    checkCategory(input.category());
    UUID id = UUID.randomUUID();
    try {
      jdbc.update("""
          INSERT INTO serms.equipment(equipment_id,asset_tag,serial_number,name,description,category,
              location,condition,requires_approval,max_borrow_duration)
          VALUES (?,?,?,?,?,?,?,?,?,CAST(? AS bigint) * interval '1 second')
          """, id, input.assetTag().strip(), optional(input.serialNumber()), input.name().strip(),
          optional(input.description()), input.category().strip(), input.location().strip(),
          input.condition(), input.requiresApproval(), seconds(input.maxBorrowDuration()));
    } catch (DataIntegrityViolationException e) { throw uniqueConflict(e); }
    audit(actor, "EQUIPMENT_REGISTER", id, "assetTag=" + input.assetTag().strip());
    return get(id);
  }

  @PreAuthorize("hasAnyRole('CUSTODIAN','ADMIN')")
  public ManagedEquipment update(UUID id, EquipmentInput input) {
    UUID actor = actor();
    if (input.expectedVersion() == null)
      throw new EquipmentManagementException(400, "expectedVersion", "The current equipment version is required.");
    var before = locked(id);
    if (before.status().equals("RETIRED"))
      throw new EquipmentManagementException(409, null, "Retired equipment cannot be changed.");
    checkCategory(input.category());
    try {
      int count = jdbc.update("""
          UPDATE serms.equipment SET asset_tag=?,serial_number=?,name=?,description=?,category=?,
              location=?,condition=?,requires_approval=?,max_borrow_duration=CAST(? AS bigint) * interval '1 second'
          WHERE equipment_id=? AND version=?
          """, input.assetTag().strip(), optional(input.serialNumber()), input.name().strip(),
          optional(input.description()), input.category().strip(), input.location().strip(),
          input.condition(), input.requiresApproval(), seconds(input.maxBorrowDuration()), id,
          input.expectedVersion());
      if (count != 1) throw new EquipmentManagementException(409, "expectedVersion", "Equipment changed. Refresh before saving.");
    } catch (DataIntegrityViolationException e) { throw uniqueConflict(e); }
    audit(actor, "EQUIPMENT_UPDATE", id,
        "assetTag=" + before.assetTag() + " -> " + input.assetTag().strip()
            + "; category=" + before.category() + " -> " + input.category().strip());
    return get(id);
  }

  @PreAuthorize("hasAnyRole('CUSTODIAN','ADMIN')")
  public ManagedEquipment retire(UUID id, long expectedVersion) {
    UUID actor = actor();
    var before = locked(id);
    if (before.status().equals("RETIRED"))
      throw new EquipmentManagementException(409, null, "Equipment is already retired.");
    if (before.status().equals("ON_LOAN") || Boolean.TRUE.equals(jdbc.queryForObject("""
        SELECT EXISTS(SELECT 1 FROM serms.loan l JOIN serms.reservation r USING(reservation_id)
          WHERE r.equipment_id=? AND l.status='ACTIVE')
        """, Boolean.class, id)))
      throw new EquipmentManagementException(409, null, "Return the active loan before retiring this equipment.");
    if (jdbc.update("UPDATE serms.equipment SET status='RETIRED' WHERE equipment_id=? AND version=?",
        id, expectedVersion) != 1)
      throw new EquipmentManagementException(409, "expectedVersion", "Equipment changed. Refresh before retiring.");
    audit(actor, "EQUIPMENT_RETIRE", id, "status=" + before.status() + " -> RETIRED");
    return get(id);
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAnyRole('CUSTODIAN','ADMIN')")
  public List<String> categories() {
    actor();
    return jdbc.queryForList("SELECT name FROM serms.equipment_category ORDER BY lower(name),name", String.class);
  }

  @PreAuthorize("hasAnyRole('CUSTODIAN','ADMIN')")
  public List<String> createCategory(String name) {
    UUID actor = actor();
    name = name.strip();
    try { jdbc.update("INSERT INTO serms.equipment_category(name) VALUES (?)", name); }
    catch (DataIntegrityViolationException e) {
      throw new EquipmentManagementException(409, "name", "This category already exists.");
    }
    audit(actor, "EQUIPMENT_CATEGORY_CREATE", categoryId(name), "name=" + name);
    return categories();
  }

  @PreAuthorize("hasAnyRole('CUSTODIAN','ADMIN')")
  public List<String> renameCategory(String oldName, String newName) {
    UUID actor = actor();
    newName = newName.strip();
    if (oldName.equals(newName)) return categories();
    try {
      if (jdbc.update("UPDATE serms.equipment_category SET name=? WHERE name=?", newName, oldName) != 1)
        throw new RecordNotFoundException();
    } catch (DataIntegrityViolationException e) {
      throw new EquipmentManagementException(409, "name", "This category already exists.");
    }
    audit(actor, "EQUIPMENT_CATEGORY_RENAME", categoryId(oldName), "name=" + oldName + " -> " + newName);
    return categories();
  }

  private ManagedEquipment locked(UUID id) {
    return jdbc.query("SELECT " + COLUMNS + " FROM serms.equipment WHERE equipment_id=? FOR UPDATE",
        (rs, row) -> map(rs), id).stream().findFirst().orElseThrow(RecordNotFoundException::new);
  }

  private ManagedEquipment get(UUID id) {
    return jdbc.query("SELECT " + COLUMNS + " FROM serms.equipment WHERE equipment_id=?",
        (rs, row) -> map(rs), id).stream().findFirst().orElseThrow(RecordNotFoundException::new);
  }

  private static ManagedEquipment map(ResultSet rs) throws SQLException {
    long seconds = rs.getLong("max_seconds");
    String duration = rs.wasNull() ? null : Duration.ofSeconds(seconds).toString();
    return new ManagedEquipment(rs.getObject("equipment_id", UUID.class), rs.getString("asset_tag"),
        rs.getString("serial_number"), rs.getString("name"), rs.getString("description"),
        rs.getString("category"), rs.getString("location"), rs.getString("condition"),
        rs.getString("status"), rs.getBoolean("requires_approval"), duration, rs.getLong("version"));
  }

  private UUID actor() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof SermsUserPrincipal principal))
      throw new AccessDeniedException("Authenticated management account required");
    UUID id = principal.getUserId();
    Boolean allowed = jdbc.queryForObject("""
        SELECT EXISTS(SELECT 1 FROM serms.app_user u JOIN serms.user_role ur USING(user_id)
          JOIN serms.role r USING(role_id) WHERE u.user_id=? AND u.account_status='ACTIVE'
          AND r.code IN ('CUSTODIAN','ADMIN'))
        """, Boolean.class, id);
    if (!Boolean.TRUE.equals(allowed)) throw new AccessDeniedException("Active management account required");
    return id;
  }

  private void checkCategory(String name) {
    if (!Boolean.TRUE.equals(jdbc.queryForObject(
        "SELECT EXISTS(SELECT 1 FROM serms.equipment_category WHERE name=?)", Boolean.class, name.strip())))
      throw new EquipmentManagementException(400, "category", "Choose an existing category.");
  }

  private static String optional(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  private static Long seconds(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      Duration duration = Duration.parse(value);
      if (duration.isNegative() || duration.isZero() || duration.getNano() != 0)
        throw new IllegalArgumentException();
      return duration.getSeconds();
    } catch (RuntimeException e) {
      throw new EquipmentManagementException(400, "maxBorrowDuration", "Enter a positive ISO-8601 duration in whole seconds.");
    }
  }

  private static EquipmentManagementException uniqueConflict(DataIntegrityViolationException e) {
    String detail = e.getMostSpecificCause().getMessage();
    if (detail != null && detail.contains("equipment_serial_number_key"))
      return new EquipmentManagementException(409, "serialNumber", "This serial number is already registered.");
    if (detail != null && detail.contains("equipment_asset_tag_key"))
      return new EquipmentManagementException(409, "assetTag", "This asset tag is already registered.");
    throw e;
  }

  private static UUID categoryId(String name) {
    return UUID.nameUUIDFromBytes(("equipment-category:" + name).getBytes(StandardCharsets.UTF_8));
  }

  private void audit(UUID actor, String action, UUID id, String summary) {
    jdbc.update("""
        INSERT INTO serms.audit_log(audit_id,actor_id,action,entity_type,entity_id,outcome,request_id,change_summary)
        VALUES (?,?,?,?,?,'SUCCESS',?,?)
        """, UUID.randomUUID(), actor, action,
        action.startsWith("EQUIPMENT_CATEGORY") ? "EquipmentCategory" : "Equipment",
        id, "equipment:" + UUID.randomUUID(), summary);
  }
}
