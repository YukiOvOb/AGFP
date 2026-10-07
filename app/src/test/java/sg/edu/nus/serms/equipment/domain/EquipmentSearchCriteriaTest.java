package sg.edu.nus.serms.equipment.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class EquipmentSearchCriteriaTest {
  static final Instant T = Instant.parse("2026-10-06T01:00:00Z");

  static EquipmentSearchCriteria window(Instant from, Instant to, boolean onlyFree) {
    return new EquipmentSearchCriteria(null, null, null, from, to, onlyFree, 0, 20);
  }

  @Test
  void blankFiltersMeanNoFilterAndTextIsTrimmed() {
    var c = new EquipmentSearchCriteria("  canon ", " ", null, null, null, false, 0, 20);
    assertThat(c.keyword()).isEqualTo("canon");
    assertThat(c.category()).isNull();
    assertThat(c.window()).isEmpty();
  }

  @Test
  void windowNeedsBothEndsInOrderAndAtMost90Days() {
    assertThat(window(T, T.plusSeconds(3600), true).window()).hasValue(new TimeWindow(T, T.plusSeconds(3600)));
    assertThat(window(T, T.plusSeconds(90L * 86400), false).window()).isPresent();
    assertThatThrownBy(() -> window(T, null, false)).isInstanceOf(InvalidSearchException.class);
    assertThatThrownBy(() -> window(null, T, false)).isInstanceOf(InvalidSearchException.class);
    assertThatThrownBy(() -> window(T, T, false))
        .isInstanceOf(InvalidSearchException.class)
        .hasMessageContaining("end after");
    assertThatThrownBy(() -> window(T, T.plusSeconds(90L * 86400 + 1), false))
        .hasMessageContaining("90 days");
  }

  @Test
  void availableOnlyNeedsAWindow() {
    assertThatThrownBy(() -> window(null, null, true)).hasMessageContaining("time window");
  }

  @Test
  void pagingAndTextLengthAreBounded() {
    assertThatThrownBy(() -> new EquipmentSearchCriteria(null, null, null, null, null, false, -1, 20))
        .isInstanceOf(InvalidSearchException.class);
    assertThatThrownBy(() -> new EquipmentSearchCriteria(null, null, null, null, null, false, 0, 51))
        .isInstanceOf(InvalidSearchException.class);
    assertThatThrownBy(() -> new EquipmentSearchCriteria("x".repeat(101), null, null, null, null, false, 0, 20))
        .isInstanceOf(InvalidSearchException.class);
  }

  @Test
  void timeWindowsAreHalfOpen() {
    var a = new TimeWindow(T, T.plusSeconds(60));
    assertThat(a.overlaps(new TimeWindow(T.plusSeconds(59), T.plusSeconds(120)))).isTrue();
    assertThat(a.overlaps(new TimeWindow(T.plusSeconds(60), T.plusSeconds(120)))).isFalse();
    assertThatThrownBy(() -> new TimeWindow(T, T)).isInstanceOf(InvalidSearchException.class);
  }

  @Test
  void searchResultCountsPages() {
    assertThat(new SearchResult(java.util.List.of(), 41, 0, 20).totalPages()).isEqualTo(3);
    assertThat(new SearchResult(java.util.List.of(), 0, 0, 20).totalPages()).isZero();
  }
}
