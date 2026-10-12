package sg.edu.nus.serms.approval.repository;

import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import sg.edu.nus.serms.approval.domain.ApprovalDecisionType;
import sg.edu.nus.serms.approval.service.*;

/** Shared database adapter for the UC02 persistence port; business policy remains in UC02. */
@Repository
@Profile("serms-postgres")
public class JdbcApprovalDecisionRepository implements ApprovalDecisionPort {
  private final JdbcTemplate jdbc;
  public JdbcApprovalDecisionRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
  @Override
  @Transactional(propagation=Propagation.MANDATORY)
  public RecordedApprovalDecision record(UUID reservationId, UUID actorId, ApprovalDecisionType decision, String comment) {
    var active=jdbc.queryForList("""
        SELECT u.user_id FROM serms.app_user u JOIN serms.user_role ur USING(user_id)
        JOIN serms.role r USING(role_id) WHERE u.user_id=? AND u.account_status='ACTIVE' AND r.code='APPROVER'
        FOR SHARE OF u,ur
        """,actorId);
    if (active.isEmpty()) throw new org.springframework.security.access.AccessDeniedException("Active approver required");
    UUID id=UUID.randomUUID();
    jdbc.update("INSERT INTO serms.approval_decision(approval_id,reservation_id,approver_id,decision,comment) VALUES (?,?,?,?,?)",
        id,reservationId,actorId,decision.name(),comment);
    var result=findByReservationId(reservationId).orElseThrow();
    jdbc.update("""
        INSERT INTO serms.audit_log(audit_id,actor_id,action,entity_type,entity_id,outcome,request_id,change_summary,occurred_at)
        VALUES (?,?,'RESERVATION_DECIDE','Reservation',?,'SUCCESS',?,?,?)
        """,UUID.randomUUID(),actorId,reservationId,"approval:"+id,decision.name(),java.sql.Timestamp.from(result.decidedAt()));
    return result;
  }
  @Override
  public Optional<RecordedApprovalDecision> findByReservationId(UUID id) {
    return jdbc.query("SELECT * FROM serms.approval_decision WHERE reservation_id=?",
        (r,n) -> new RecordedApprovalDecision(r.getObject("approval_id",UUID.class),id,
        r.getObject("approver_id",UUID.class),ApprovalDecisionType.valueOf(r.getString("decision")),
        r.getString("comment"),r.getTimestamp("decided_at").toInstant()),id).stream().findFirst();
  }
}
