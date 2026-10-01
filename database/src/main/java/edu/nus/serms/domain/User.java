package edu.nus.serms.domain;
import java.util.Set;
import java.util.UUID;

/** Credential-free projection of app_user and its many-to-many roles. */
public record User(UUID userId, String email, String displayName, AccountStatus accountStatus, Set<Role> roles) {
    public User { roles = Set.copyOf(roles); }
    public enum AccountStatus { ACTIVE, DISABLED }
    public enum Role { BORROWER, APPROVER, CUSTODIAN, MAINTAINER, ADMIN }
}
