package edu.nus.serms.domain;
import java.util.UUID;
public record Equipment(UUID equipmentId, String assetTag, String name, String category,
                        String location, Status status, boolean requiresApproval, int version) {
    public enum Status { AVAILABLE, ON_LOAN, UNDER_MAINTENANCE, RETIRED }
}
