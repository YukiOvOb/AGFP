package sg.edu.nus.serms.loan.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.serms.loan.domain.*;
import sg.edu.nus.serms.maintenance.domain.FaultCategory;
import sg.edu.nus.serms.maintenance.domain.MaintenancePriority;
import sg.edu.nus.serms.maintenance.service.MaintenanceService;
import sg.edu.nus.serms.shared.domain.RecordNotFoundException;
import sg.edu.nus.serms.shared.security.SermsUserPrincipal;

@Service
@Transactional
public class LoanService {
  /** Fault fields for a damaged return (#21); proposed by the UC04 owner, pending UC03 confirmation. */
  static final FaultCategory DAMAGED_RETURN_CATEGORY = FaultCategory.PHYSICAL_DAMAGE;
  static final MaintenancePriority DAMAGED_RETURN_PRIORITY = MaintenancePriority.HIGH;
  private final JdbcTemplate jdbc;
  private final Clock clock;
  private final MaintenanceService maintenance;
  private final ApplicationEventPublisher events;
  private static final String VIEW = """
      SELECT l.*, r.equipment_id, r.requester_id, e.name AS equipment_name, e.asset_tag,
             u.display_name AS borrower_name
      FROM serms.loan l JOIN serms.reservation r USING(reservation_id)
      JOIN serms.equipment e USING(equipment_id)
      JOIN serms.app_user u ON u.user_id=r.requester_id
      """;

  public LoanService(JdbcTemplate jdbc, @Qualifier("clock") Clock clock,
      MaintenanceService maintenance, ApplicationEventPublisher events) {
    this.jdbc = jdbc; this.clock = clock; this.maintenance = maintenance; this.events = events;
  }

  @PreAuthorize("hasRole('CUSTODIAN')")
  public LoanView checkout(UUID reservationId, UUID borrowerId, long expectedVersion,
      boolean identityConfirmed) {
    if (!identityConfirmed || borrowerId == null || expectedVersion < 0)
      throw new IllegalArgumentException("Confirm the borrower's identity and reservation version");
    UUID equipmentId = reference("SELECT equipment_id FROM serms.reservation WHERE reservation_id=?", reservationId);
    var state = lockEquipment(equipmentId);
    var rows = jdbc.queryForList("SELECT * FROM serms.reservation WHERE reservation_id=? FOR UPDATE", reservationId);
    if (rows.isEmpty()) throw new RecordNotFoundException();
    var r = rows.get(0);
    UUID actor = requireActor();
    if (!borrowerId.equals(r.get("requester_id")))
      throw new LoanConflictException("The borrower does not match this reservation.");
    requireActiveBorrower(borrowerId);
    if (((Number) r.get("version")).longValue() != expectedVersion)
      throw new LoanConflictException("The reservation changed. Refresh before handing over equipment.");
    Instant now = now();
    Instant start = ((Timestamp) r.get("start_at")).toInstant();
    Instant due = ((Timestamp) r.get("end_at")).toInstant();
    if (!"CONFIRMED".equals(r.get("status")) || now.isBefore(start) || !now.isBefore(due))
      throw new LoanConflictException("Checkout requires a confirmed reservation within its time window.");
    if (!state.permitsCheckout() || activeMaintenance(equipmentId) || activeLoan(equipmentId))
      throw new LoanConflictException("Equipment is unavailable for physical handover.");
    UUID loanId = UUID.randomUUID();
    jdbc.update("""
        INSERT INTO serms.loan(loan_id,reservation_id,checkout_by,checked_out_at,due_at,status)
        VALUES (?,?,?,?,?,'ACTIVE')
        """, loanId, reservationId, actor, Timestamp.from(now), Timestamp.from(due));
    if (jdbc.update("UPDATE serms.reservation SET status='FULFILLED' WHERE reservation_id=? AND version=?",
        reservationId, expectedVersion) != 1) throw new LoanConflictException("Reservation changed.");
    setEquipment(equipmentId, state, EquipmentLoanState.ON_LOAN);
    audit(actor, "LOAN_CHECKOUT", loanId, now, "Confirmed physical handover to " + borrowerId);
    events.publishEvent(new LoanEvent("CHECKED_OUT", loanId, reservationId, equipmentId,
        borrowerId, actor, due, now, false, null, null));
    return load(loanId, now);
  }

  @PreAuthorize("hasRole('CUSTODIAN')")
  public LoanView receive(UUID loanId, long expectedVersion, String condition, String note) {
    if (expectedVersion < 0 || !("GOOD".equals(condition) || "DAMAGED".equals(condition))
        || (note != null && note.length() > 2000)
        || ("DAMAGED".equals(condition) && (note == null || note.isBlank())))
      throw new IllegalArgumentException("Damage requires a description; choose GOOD or DAMAGED");
    UUID reservationId = reference("SELECT reservation_id FROM serms.loan WHERE loan_id=?", loanId);
    UUID equipmentId = reference("SELECT equipment_id FROM serms.reservation WHERE reservation_id=?", reservationId);
    var state = lockEquipment(equipmentId);
    jdbc.queryForList("SELECT reservation_id FROM serms.reservation WHERE reservation_id=? FOR UPDATE", reservationId);
    jdbc.queryForList("SELECT loan_id FROM serms.loan WHERE loan_id=? FOR UPDATE", loanId);
    UUID actor = requireActor();
    Instant now = now();
    var before = load(loanId, now);
    if (!before.status().equals("ACTIVE") || before.version() != expectedVersion)
      throw new LoanConflictException("This loan was returned or changed. Refresh before receiving it.");
    if (now.isBefore(before.checkedOutAt())) throw new LoanConflictException("Return cannot precede checkout.");
    if (jdbc.update("""
        UPDATE serms.loan SET status='RETURNED',return_by=?,returned_at=?,return_condition=?,return_note=?
        WHERE loan_id=? AND version=? AND status='ACTIVE'
        """, actor, Timestamp.from(now), condition, note == null ? null : note.strip(), loanId, expectedVersion) != 1)
      throw new LoanConflictException("Loan changed.");
    UUID caseId = null;
    // A retired item can still be physically received; an OPEN damage case preserves evidence.
    // MaintenanceService rejects new faults on retired items, so create that case here atomically.
    if ("DAMAGED".equals(condition)) {
      if (state == EquipmentLoanState.RETIRED) {
        caseId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO serms.maintenance_case(maintenance_case_id,equipment_id,reported_by,loan_id,
                fault_description,reported_at,fault_category,priority) VALUES (?,?,?,?,?,?,?,?)
            """, caseId, equipmentId, actor, loanId, note.strip(), Timestamp.from(now),
            DAMAGED_RETURN_CATEGORY.name(), DAMAGED_RETURN_PRIORITY.name());
        auditCase(actor, caseId, now, note.strip());
      } else {
        caseId = maintenance.reportFault(equipmentId, actor, note.strip(), loanId,
            DAMAGED_RETURN_CATEGORY, DAMAGED_RETURN_PRIORITY).maintenanceCaseId();
      }
    }
    // Reload state: reportFault may already have changed it.
    state = lockEquipment(equipmentId);
    setEquipment(equipmentId, state, state.afterReturn(activeMaintenance(equipmentId), activeLoan(equipmentId)));
    boolean late = now.isAfter(before.dueAt());
    audit(actor, "LOAN_RETURN", loanId, now, "condition=" + condition + "; late=" + late + "; maintenanceCase=" + caseId);
    events.publishEvent(new LoanEvent("RETURNED", loanId, reservationId, equipmentId,
        before.borrowerId(), actor, before.dueAt(), now, late, condition, caseId));
    return load(loanId, now);
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAnyRole('BORROWER','CUSTODIAN')")
  public List<LoanView> list(boolean activeOnly, int page, int size, String q) {
    if (page < 0 || page > 100000 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid page");
    UUID actor = requireReader();
    boolean custodian = activeRole(actor, "CUSTODIAN");
    String where = " WHERE (?=false OR l.status='ACTIVE')";
    if (!custodian) where += " AND r.requester_id=?";
    if (searchPattern(q) != null) where += " AND (r.reservation_id::text ILIKE ? ESCAPE '\\' OR u.display_name ILIKE ? ESCAPE '\\' OR u.email ILIKE ? ESCAPE '\\' OR e.asset_tag ILIKE ? ESCAPE '\\')";
    String sql = VIEW + where + " ORDER BY l.checked_out_at DESC,l.loan_id LIMIT ? OFFSET ?";
    Instant now = now();
    var args = new java.util.ArrayList<Object>();
    args.add(activeOnly);
    if (!custodian) args.add(actor);
    if (searchPattern(q) != null) for (int i = 0; i < 4; i++) args.add(searchPattern(q));
    args.add(size); args.add(page * size);
    return jdbc.query(sql, (r, n) -> map(r, now), args.toArray());
  }

  public List<LoanView> list(boolean activeOnly, int page, int size) {
    return list(activeOnly, page, size, null);
  }

  private LoanView load(UUID id, Instant now) {
    return jdbc.query(VIEW + " WHERE l.loan_id=?", (r, n) -> map(r, now), id)
        .stream().findFirst().orElseThrow(RecordNotFoundException::new);
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasRole('CUSTODIAN')")
  public List<CheckoutCandidate> checkoutQueue(int page, int size, String q) {
    if (page < 0 || page > 100000 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid page");
    UUID actor = requireReader();
    if (!activeRole(actor, "CUSTODIAN")) throw new AccessDeniedException("Active custodian required");
    String sql = """
        SELECT r.*,e.name,e.asset_tag,u.display_name FROM serms.reservation r
        JOIN serms.equipment e USING(equipment_id)
        JOIN serms.app_user u ON u.user_id=r.requester_id
        WHERE r.status='CONFIRMED' AND r.end_at>?
        """;
    var args = new java.util.ArrayList<Object>();
    args.add(Timestamp.from(now()));
    if (searchPattern(q) != null) {
      sql += " AND (r.reservation_id::text ILIKE ? ESCAPE '\\' OR u.display_name ILIKE ? ESCAPE '\\' OR u.email ILIKE ? ESCAPE '\\' OR e.asset_tag ILIKE ? ESCAPE '\\')";
      for (int i = 0; i < 4; i++) args.add(searchPattern(q));
    }
    sql += " ORDER BY r.start_at,r.reservation_id LIMIT ? OFFSET ?";
    args.add(size); args.add(page * size);
    return jdbc.query(sql, (r,n) -> new CheckoutCandidate(r.getObject("reservation_id",UUID.class),
        r.getObject("equipment_id",UUID.class),r.getString("name"),r.getString("asset_tag"),r.getObject("requester_id",UUID.class),
        r.getString("display_name"),r.getTimestamp("start_at").toInstant(),r.getTimestamp("end_at").toInstant(),
        r.getLong("version")),args.toArray());
  }

  public List<CheckoutCandidate> checkoutQueue(int page, int size) {
    return checkoutQueue(page, size, null);
  }

  private static String searchPattern(String q) {
    if (q == null || q.isBlank()) return null;
    String trimmed = q.strip();
    if (trimmed.length() > 100) throw new IllegalArgumentException("Search must have at most 100 characters");
    return "%" + trimmed.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasAnyRole('BORROWER','CUSTODIAN')")
  public LoanView get(UUID id) {
    UUID actor = requireReader();
    var loan = load(id, now());
    if (!activeRole(actor,"CUSTODIAN") && !loan.borrowerId().equals(actor))
      throw new RecordNotFoundException();
    return loan;
  }

  private static LoanView map(java.sql.ResultSet r, Instant now) throws java.sql.SQLException {
    var returned = r.getTimestamp("returned_at");
    Instant due = r.getTimestamp("due_at").toInstant();
    return new LoanView(r.getObject("loan_id",UUID.class), r.getObject("reservation_id",UUID.class),
        r.getObject("equipment_id",UUID.class), r.getString("equipment_name"), r.getString("asset_tag"),
        r.getObject("requester_id",UUID.class), r.getString("borrower_name"),
        r.getObject("checkout_by",UUID.class), r.getObject("return_by",UUID.class),
        r.getTimestamp("checked_out_at").toInstant(), due, returned == null ? null : returned.toInstant(),
        r.getString("status"), r.getString("return_condition"), r.getString("return_note"), r.getLong("version"),
        (returned == null ? now : returned.toInstant()).isAfter(due));
  }

  private UUID reference(String sql, UUID id) {
    return jdbc.query(sql, (r,n) -> r.getObject(1,UUID.class), id).stream().findFirst()
        .orElseThrow(RecordNotFoundException::new);
  }
  private EquipmentLoanState lockEquipment(UUID id) {
    return jdbc.query("SELECT status FROM serms.equipment WHERE equipment_id=? FOR UPDATE",
        (r,n) -> EquipmentLoanState.valueOf(r.getString(1)), id).stream().findFirst()
        .orElseThrow(RecordNotFoundException::new);
  }
  private boolean activeMaintenance(UUID id) {
    return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM serms.maintenance_case WHERE equipment_id=? AND status IN ('OPEN','ASSIGNED','IN_PROGRESS'))", Boolean.class,id));
  }
  private boolean activeLoan(UUID id) {
    return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM serms.loan l JOIN serms.reservation r USING(reservation_id) WHERE r.equipment_id=? AND l.status='ACTIVE')",Boolean.class,id));
  }
  private void setEquipment(UUID id, EquipmentLoanState before, EquipmentLoanState after) {
    if (before != after) jdbc.update("UPDATE serms.equipment SET status=? WHERE equipment_id=?",after.name(),id);
  }
  private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }
  private UUID principal() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof SermsUserPrincipal me))
      throw new AccessDeniedException("Authenticated session required");
    return me.getUserId();
  }
  private UUID requireActor() {
    UUID id = principal();
    if (!lockedActiveRole(id, "CUSTODIAN")) throw new AccessDeniedException("Active custodian required");
    return id;
  }
  private UUID requireReader() {
    UUID id = principal();
    if (!activeRole(id,"BORROWER") && !activeRole(id,"CUSTODIAN")) throw new AccessDeniedException("Active account required");
    return id;
  }
  private boolean activeRole(UUID id, String role) {
    return Boolean.TRUE.equals(jdbc.queryForObject("""
        SELECT EXISTS(SELECT 1 FROM serms.app_user u JOIN serms.user_role ur USING(user_id)
        JOIN serms.role r USING(role_id) WHERE u.user_id=? AND u.account_status='ACTIVE' AND r.code=?)
        """, Boolean.class,id,role));
  }
  private void requireActiveBorrower(UUID id) {
    if (!lockedActiveRole(id,"BORROWER")) throw new LoanConflictException("The borrower is inactive or has no BORROWER role.");
  }
  private boolean lockedActiveRole(UUID id, String role) {
    return !jdbc.queryForList("""
        SELECT u.user_id FROM serms.app_user u JOIN serms.user_role ur USING(user_id)
        JOIN serms.role r USING(role_id) WHERE u.user_id=? AND u.account_status='ACTIVE' AND r.code=?
        FOR SHARE OF u,ur
        """,id,role).isEmpty();
  }
  private void audit(UUID actor, String action, UUID id, Instant now, String summary) {
    jdbc.update("""
        INSERT INTO serms.audit_log(audit_id,actor_id,action,entity_type,entity_id,outcome,request_id,change_summary,occurred_at)
        VALUES (?,?,?,'Loan',?,'SUCCESS',?,?,?)
        """, UUID.randomUUID(), actor, action, id, "loan:" + UUID.randomUUID(), summary, Timestamp.from(now));
  }
  private void auditCase(UUID actor, UUID id, Instant now, String note) {
    jdbc.update("""
        INSERT INTO serms.audit_log(audit_id,actor_id,action,entity_type,entity_id,outcome,request_id,change_summary,occurred_at)
        VALUES (?,?,'MAINTENANCE_REPORT_FAULT','MaintenanceCase',?,'SUCCESS',?,?,?)
        """,UUID.randomUUID(),actor,id,"loan-damage:" + UUID.randomUUID(),note,Timestamp.from(now));
  }
}
