package sg.edu.nus.serms.maintenance.domain;

/** Fault report priority (Proposal 5.6), lowest first. Existing cases before V006 are {@link #MEDIUM}. */
public enum MaintenancePriority { LOW, MEDIUM, HIGH, URGENT }
