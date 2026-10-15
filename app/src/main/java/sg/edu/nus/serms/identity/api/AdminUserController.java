package sg.edu.nus.serms.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import sg.edu.nus.serms.identity.domain.AdminUser;
import sg.edu.nus.serms.identity.domain.AdminUser.Role;
import sg.edu.nus.serms.identity.service.AdminUserService;
import sg.edu.nus.serms.shared.security.SermsUserPrincipal;

@RestController
@RequestMapping(value = "/api/v1/admin/users", produces = "application/json")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
  public record CreateUserRequest(
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(max = 200) String displayName,
      @NotBlank @Size(min = 12, max = 72) String initialPassword,
      @NotEmpty Set<@NotNull Role> roles) {
    @Override public String toString() { return "CreateUserRequest[REDACTED]"; }
  }

  public record UpdateDisplayNameRequest(
      @NotBlank @Size(max = 200) String displayName,
      @NotBlank @Size(max = 200) String expectedDisplayName) {}

  private final AdminUserService users;
  public AdminUserController(AdminUserService users) { this.users = users; }

  @GetMapping
  @Operation(operationId = "adminListUsers")
  public AdminUser.Page list(@AuthenticationPrincipal SermsUserPrincipal me,
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return users.list(me.getUserId(), search, page, size);
  }

  @PostMapping
  @Operation(operationId = "adminCreateUser")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Account created")
  public ResponseEntity<AdminUser> create(@AuthenticationPrincipal SermsUserPrincipal me,
      @Valid @RequestBody CreateUserRequest request) {
    var created = users.create(me.getUserId(), request.email(), request.displayName(),
        request.initialPassword(), request.roles());
    return ResponseEntity.status(201).body(created);
  }

  @PatchMapping("/{userId}/display-name")
  @Operation(operationId = "adminUpdateDisplayName")
  public AdminUser updateDisplayName(@AuthenticationPrincipal SermsUserPrincipal me,
      @PathVariable UUID userId, @Valid @RequestBody UpdateDisplayNameRequest request) {
    return users.updateDisplayName(me.getUserId(), userId, request.displayName(), request.expectedDisplayName());
  }
}
