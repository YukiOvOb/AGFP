package sg.edu.nus.serms.maintenance.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record FaultReport(
    UUID faultReportId,
    UUID equipmentId,
    String faultCategory,
    String description,
    UUID reporterId,
    Instant reportedAt,
    String priority) {
  public FaultReport {
    Objects.requireNonNull(faultReportId, "faultReportId");
    Objects.requireNonNull(equipmentId, "equipmentId");
    Objects.requireNonNull(reporterId, "reporterId");
    Objects.requireNonNull(reportedAt, "reportedAt");
    faultCategory = requiredText(faultCategory, "faultCategory");
    description = requiredText(description, "description");
    priority = requiredText(priority, "priority");
  }

  private static String requiredText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.strip();
  }
}
