package sg.edu.nus.serms.audit.service;

import java.time.Instant;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.serms.audit.domain.AuditEntry;
import sg.edu.nus.serms.audit.repository.AuditQueryRepository;
import sg.edu.nus.serms.identity.domain.AdminUserRules;
import sg.edu.nus.serms.identity.service.AdminAccess;

@Service
public class AuditQueryService {
  private final AuditQueryRepository audit;
  private final AdminAccess access;
  public AuditQueryService(AuditQueryRepository audit, AdminAccess access) {
    this.audit = audit; this.access = access;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  @PreAuthorize("hasRole('ADMIN')")
  public AuditEntry.Page list(UUID operator, UUID actor, String action, String entityType,
      Instant from, Instant to, int page, int size) {
    access.requireAdministrator(operator);
    AdminUserRules.page(page, size);
    String actionFilter = filter(action), entityFilter = filter(entityType);
    if (from != null && to != null && !from.isBefore(to))
      throw new IllegalArgumentException("The start must precede the end");
    return audit.list(actor, actionFilter, entityFilter, from, to, page, size);
  }

  private String filter(String value) {
    String normalized = value == null ? "" : value.strip();
    if (normalized.length() > 100) throw new IllegalArgumentException("Filter is too long");
    return normalized;
  }
}
