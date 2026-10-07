package sg.edu.nus.serms.equipment.domain;

import java.util.UUID;

/**
 * One search result. {@code availableInWindow} is {@code null} when the search had no time window.
 */
public record EquipmentSummary(
    UUID equipmentId,
    String assetTag,
    String name,
    String category,
    String location,
    EquipmentStatus status,
    boolean requiresApproval,
    Boolean availableInWindow) {}
