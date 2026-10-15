package sg.edu.nus.serms.identity.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Deliberately excludes credentials and notification delivery identifiers. */
public record AdminUser(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID userId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String accountStatus,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Set<Role> roles,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version) {
  public AdminUser { roles = Set.copyOf(roles); }
  public AdminUser(UUID userId, String email, String displayName, String accountStatus, Set<Role> roles) {
    this(userId, email, displayName, accountStatus, roles, 0L);
  }

  @Schema(name = "AdminUserRole")
  public enum Role { BORROWER, APPROVER, CUSTODIAN, MAINTAINER, ADMIN }

  @Schema(name = "AdminUserPage")
  public record Page(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<AdminUser> content,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalElements,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int size) {}
}
