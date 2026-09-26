package sg.edu.nus.serms.notification.service;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record NotificationPage(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<NotificationView> content,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int size,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalElements,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalPages) {}
