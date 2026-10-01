package sg.edu.nus.serms.notification.controller;

import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import sg.edu.nus.serms.notification.service.NotificationInboxService;

@Controller
public class NotificationController {
  private final NotificationInboxService inbox;

  public NotificationController(NotificationInboxService inbox) {
    this.inbox = inbox;
  }

  @GetMapping("/notifications")
  String inbox(Principal principal, @RequestParam(defaultValue = "0") int page, Model model) {
    int current = Math.max(0, Math.min(page, 10000));
    model.addAttribute("notifications", inbox.inbox(principal.getName(), current));
    model.addAttribute("page", current);
    return "notifications/inbox";
  }

  @PostMapping("/notifications/{id}/read")
  String read(@PathVariable String id, Principal principal) {
    inbox.markRead(id, principal.getName());
    return "redirect:/notifications";
  }
}
