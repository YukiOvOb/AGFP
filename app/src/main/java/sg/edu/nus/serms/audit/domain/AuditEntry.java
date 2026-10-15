package sg.edu.nus.serms.audit.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AuditEntry(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID auditId,
    UUID actorId, String actorName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String action,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String entityType,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID entityId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String outcome,
    String changeSummary,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant occurredAt) {
  @Schema(name = "AuditEntryPage")
  public record Page(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<AuditEntry> content,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalElements,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int size) {}
}
