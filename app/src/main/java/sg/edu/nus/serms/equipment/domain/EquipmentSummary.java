package sg.edu.nus.serms.equipment.domain;

import java.time.Duration;
import java.util.UUID;

/**
 * One search result. {@code availableInWindow} is {@code null} when the search had no time window.
 * {@code description} is {@code null} when none is recorded; {@code maxBorrowDuration} is {@code
 * null} when the global reservation limit applies.
 */
public record EquipmentSummary(
    UUID equipmentId,
    String assetTag,
    String name,
    String category,
    String location,
    EquipmentStatus status,
    boolean requiresApproval,
    Boolean availableInWindow,
    String description,
    Duration maxBorrowDuration) {}
