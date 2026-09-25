package sg.edu.nus.serms.notification.service;

import sg.edu.nus.serms.notification.domain.NotificationRequest;

/**
 * Publish inside an active producer transaction. The listener persists the request before commit.
 */
public record NotificationRequested(NotificationRequest request) {}
