package edu.nus.serms.domain;

import java.util.UUID;

public record Equipment(UUID id, String assetTag, String name, String category,
                        String location, Status status, boolean requiresApproval) {
    public enum Status { AVAILABLE, ON_LOAN, MAINTENANCE, RETIRED }
}