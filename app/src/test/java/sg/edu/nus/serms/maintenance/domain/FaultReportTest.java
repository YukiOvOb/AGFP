package sg.edu.nus.serms.maintenance.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FaultReportTest {
  private static final UUID REPORT_ID = new UUID(0, 1);
  private static final UUID EQUIPMENT_ID = new UUID(0, 2);
  private static final UUID REPORTER_ID = new UUID(0, 3);
  private static final Instant REPORTED_AT = Instant.parse("2026-09-29T08:00:00Z");

  @Test
  void retainsRequiredFieldsAndTrimsText() {
    FaultReport report =
        new FaultReport(
            REPORT_ID, EQUIPMENT_ID, "  electrical  ", "  Does not start  ", REPORTER_ID,
            REPORTED_AT, "  urgent  ");

    assertThat(report.faultReportId()).isEqualTo(REPORT_ID);
    assertThat(report.equipmentId()).isEqualTo(EQUIPMENT_ID);
    assertThat(report.reporterId()).isEqualTo(REPORTER_ID);
    assertThat(report.reportedAt()).isEqualTo(REPORTED_AT);
    assertThat(report.faultCategory()).isEqualTo("electrical");
    assertThat(report.description()).isEqualTo("Does not start");
    assertThat(report.priority()).isEqualTo("urgent");
  }

  @Test
  void rejectsMissingIdentifiersAndTime() {
    assertThatNullPointerException()
        .isThrownBy(() -> new FaultReport(null, EQUIPMENT_ID, "c", "d", REPORTER_ID, REPORTED_AT, "p"));
    assertThatNullPointerException()
        .isThrownBy(() -> new FaultReport(REPORT_ID, null, "c", "d", REPORTER_ID, REPORTED_AT, "p"));
    assertThatNullPointerException()
        .isThrownBy(() -> new FaultReport(REPORT_ID, EQUIPMENT_ID, "c", "d", null, REPORTED_AT, "p"));
    assertThatNullPointerException()
        .isThrownBy(() -> new FaultReport(REPORT_ID, EQUIPMENT_ID, "c", "d", REPORTER_ID, null, "p"));
  }

  @Test
  void rejectsBlankOrNullText() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new FaultReport(REPORT_ID, EQUIPMENT_ID, null, "d", REPORTER_ID, REPORTED_AT, "p"));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new FaultReport(REPORT_ID, EQUIPMENT_ID, "  ", "d", REPORTER_ID, REPORTED_AT, "p"));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new FaultReport(REPORT_ID, EQUIPMENT_ID, "c", "", REPORTER_ID, REPORTED_AT, "p"));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new FaultReport(REPORT_ID, EQUIPMENT_ID, "c", "d", REPORTER_ID, REPORTED_AT, "\t"));
  }

  @Test
  void recordExposesNoMutableState() {
    FaultReport report =
        new FaultReport(REPORT_ID, EQUIPMENT_ID, "category", "description", REPORTER_ID,
            REPORTED_AT, "priority");
    assertThat(report)
        .isEqualTo(new FaultReport(REPORT_ID, EQUIPMENT_ID, "category", "description", REPORTER_ID,
            REPORTED_AT, "priority"));
    assertThat(FaultReport.class.getDeclaredFields())
        .allSatisfy(field -> assertThat(java.lang.reflect.Modifier.isFinal(field.getModifiers())).isTrue());
  }
}
