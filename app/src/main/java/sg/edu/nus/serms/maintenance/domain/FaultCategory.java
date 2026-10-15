package sg.edu.nus.serms.maintenance.domain;

/** Fault report category (Proposal 5.6). Existing cases before V006 are {@link #OTHER}. */
public enum FaultCategory { MECHANICAL, ELECTRICAL, SOFTWARE, PHYSICAL_DAMAGE, MISSING_PART, OTHER }
