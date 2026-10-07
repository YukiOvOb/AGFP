package sg.edu.nus.serms.equipment.domain;

import java.time.Instant;
import java.util.Objects;

/** Half-open interval {@code [start, end)}, matching {@code tstzrange(start, end, '[)')}. */
public record TimeWindow(Instant start, Instant end) {
  public TimeWindow {
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(end, "end");
    if (!start.isBefore(end)) throw new InvalidSearchException("The window must end after it starts");
  }

  public boolean overlaps(TimeWindow other) {
    return start.isBefore(other.end) && other.start.isBefore(end);
  }
}
