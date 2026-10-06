package sg.edu.nus.serms.notification.api;

import io.swagger.v3.oas.annotations.Operation;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sg.edu.nus.serms.notification.service.*;
import sg.edu.nus.serms.shared.security.SermsUserPrincipal;

@RestController
@RequestMapping(value = "/api/v1/notifications", produces = "application/json")
public class NotificationController {
  public record UnreadCount(
      @io.swagger.v3.oas.annotations.media.Schema(
              requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
          long count) {}

  private final NotificationInboxService inbox;

  public NotificationController(NotificationInboxService inbox) {
    this.inbox = inbox;
  }

  @GetMapping
  @Operation(operationId = "listNotifications")
  public NotificationPage list(
      @AuthenticationPrincipal SermsUserPrincipal me,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "deliveredAt,desc") String sort) {
    return inbox.page(me.getUserId(), page, size, sort);
  }

  @GetMapping("/unread-count")
  @Operation(operationId = "unreadCount")
  public UnreadCount unread(@AuthenticationPrincipal SermsUserPrincipal me) {
    return new UnreadCount(inbox.unread(me.getUserId()));
  }

  @PostMapping("/{id}/read")
  @Operation(operationId = "markNotificationRead")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "204",
      description = "Marked as read")
  public ResponseEntity<Void> read(
      @PathVariable UUID id, @AuthenticationPrincipal SermsUserPrincipal me) {
    inbox.markReadForUser(id, me.getUserId());
    return ResponseEntity.noContent().build();
  }
}
