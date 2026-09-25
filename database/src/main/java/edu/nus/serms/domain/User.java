package edu.nus.serms.domain;

import java.util.UUID;

/** Safe user projection; credential hashes belong only in the authentication repository. */
public record User(UUID id, String email, String displayName, Role role, boolean active) {
    public enum Role { BORROWER, APPROVER, TECHNICIAN, ADMIN }
}