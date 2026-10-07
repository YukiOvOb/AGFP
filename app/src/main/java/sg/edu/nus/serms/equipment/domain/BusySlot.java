package sg.edu.nus.serms.equipment.domain;

import java.time.Instant;

/** A period in which the equipment is already held. Borrower identity is deliberately absent. */
public record BusySlot(Instant startAt, Instant endAt, String reason) {}
