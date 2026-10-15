package sg.edu.nus.serms.notification.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import sg.edu.nus.serms.shared.security.SermsUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only UC05 projection of active loans past their persisted due time. */
@Service
public class OverdueLoanService {
  public record OverdueLoan(UUID loanId, UUID reservationId, String borrowerName,
      String equipmentName, Instant dueAt, long overdueSeconds) {}
  public record OverduePage(List<OverdueLoan> content, long totalElements, int page, int size) {}
  private final JdbcTemplate jdbc;
  private final Clock clock;

  public OverdueLoanService(JdbcTemplate jdbc, Clock clock) { this.jdbc=jdbc; this.clock=clock; }

  @PreAuthorize("hasAnyRole('CUSTODIAN','ADMIN')")
  @Transactional(readOnly=true)
  public OverduePage list(int page, int size, String order) {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()
        || !(authentication.getPrincipal() instanceof SermsUserPrincipal principal))
      throw new AccessDeniedException("Authenticated session required");
    Boolean active = jdbc.queryForObject("""
        SELECT EXISTS(SELECT 1 FROM serms.app_user u
          JOIN serms.user_role ur USING (user_id) JOIN serms.role r USING (role_id)
          WHERE u.user_id=? AND u.account_status='ACTIVE' AND r.code IN ('CUSTODIAN','ADMIN'))
        """, Boolean.class, principal.getUserId());
    if (!Boolean.TRUE.equals(active)) throw new AccessDeniedException("Active operator role required");
    if (page<0 || page>100000 || size<1 || size>100) throw new IllegalArgumentException("Invalid page or size");
    if (!"desc".equals(order) && !"asc".equals(order)) throw new IllegalArgumentException("Invalid overdue order");
    Instant now=clock.instant();
    Long total=jdbc.queryForObject("SELECT count(*) FROM serms.loan WHERE status='ACTIVE' AND due_at<?",
        Long.class,Timestamp.from(now));
    String direction = "desc".equals(order) ? "ASC" : "DESC";
    var rows=jdbc.query("""
        SELECT l.loan_id,l.reservation_id,u.display_name,e.name,l.due_at
        FROM serms.loan l JOIN serms.reservation r USING (reservation_id)
        JOIN serms.app_user u ON u.user_id=r.requester_id
        JOIN serms.equipment e ON e.equipment_id=r.equipment_id
        WHERE l.status='ACTIVE' AND l.due_at<?
        ORDER BY l.due_at %s,l.loan_id ASC LIMIT ? OFFSET ?
        """.formatted(direction),(rs,row)->{
          Instant due=rs.getTimestamp(5).toInstant();
          return new OverdueLoan(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),
              rs.getString(3),rs.getString(4),due,Duration.between(due,now).getSeconds());
        },Timestamp.from(now),size,(long)page*size);
    return new OverduePage(rows,total==null?0:total,page,size);
  }
}
