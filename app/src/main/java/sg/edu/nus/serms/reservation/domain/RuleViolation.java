package sg.edu.nus.serms.reservation.domain;

/** A broken reservation rule: a stable {@code code} for clients and a message for people. */
public record RuleViolation(String code, String message) {}
