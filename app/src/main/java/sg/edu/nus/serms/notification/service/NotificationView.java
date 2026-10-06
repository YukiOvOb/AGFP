package sg.edu.nus.serms.notification.service;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import sg.edu.nus.serms.notification.domain.*;

public record NotificationView(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) NotificationType type,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String content,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant deliveredAt,
    @Schema(nullable = true) Instant readAt) {
  public static NotificationView from(Notification n) {
    return new NotificationView(
        n.getId(), n.getType(), n.getContent(), n.getDeliveredAt(), n.getReadAt());
  }
}
