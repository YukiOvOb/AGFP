package sg.edu.nus.serms.audit.api;

import io.swagger.v3.oas.annotations.Operation;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sg.edu.nus.serms.audit.domain.AuditEntry;
import sg.edu.nus.serms.audit.service.AuditQueryService;
import sg.edu.nus.serms.shared.security.SermsUserPrincipal;

@RestController
@RequestMapping(value = "/api/v1/admin/audit", produces = "application/json")
@PreAuthorize("hasRole('ADMIN')")
public class AuditQueryController {
  private final AuditQueryService audit;
  public AuditQueryController(AuditQueryService audit) { this.audit = audit; }

  @GetMapping
  @Operation(operationId = "adminListAudit")
  public AuditEntry.Page list(@AuthenticationPrincipal SermsUserPrincipal me,
      @RequestParam(required = false) UUID actorId,
      @RequestParam(defaultValue = "") String action,
      @RequestParam(defaultValue = "") String entityType,
      @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return audit.list(me.getUserId(), actorId, action, entityType, from, to, page, size);
  }
}
