package sg.edu.nus.serms.equipment.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Validated search input for UC01. A time window is optional; when present both ends are required
 * and the start must be before the end. Keyword, category and location are trimmed and blank
 * values are treated as "no filter".
 */
public record EquipmentSearchCriteria(
    String keyword,
    String category,
    String location,
    Instant from,
    Instant to,
    boolean availableOnly,
    int page,
    int size) {

  public static final int MAX_PAGE_SIZE = 50;
  public static final Duration MAX_WINDOW = Duration.ofDays(90);

  public EquipmentSearchCriteria {
    keyword = clean(keyword, 100);
    category = clean(category, 100);
    location = clean(location, 100);
    if ((from == null) != (to == null)) {
      throw new InvalidSearchException("Both ends of the time window are required");
    }
    if (from != null && !from.isBefore(to)) {
      throw new InvalidSearchException("The time window must end after it starts");
    }
    if (from != null && Duration.between(from, to).compareTo(MAX_WINDOW) > 0) {
      throw new InvalidSearchException("The time window may span at most 90 days");
    }
    if (availableOnly && from == null) {
      throw new InvalidSearchException("Choose a time window to show only available equipment");
    }
    if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
      throw new InvalidSearchException("Invalid page request");
    }
  }

  public Optional<TimeWindow> window() {
    return from == null ? Optional.empty() : Optional.of(new TimeWindow(from, to));
  }

  private static String clean(String value, int max) {
    if (value == null || value.isBlank()) return null;
    String trimmed = value.strip();
    if (trimmed.length() > max) throw new InvalidSearchException("Search text is too long");
    return trimmed;
  }
}
