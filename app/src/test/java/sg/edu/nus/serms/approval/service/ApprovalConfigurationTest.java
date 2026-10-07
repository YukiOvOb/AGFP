package sg.edu.nus.serms.approval.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ApprovalConfigurationTest {
  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withUserConfiguration(ApprovalConfiguration.class);

  @Test
  void defaultApplicationDoesNotRequireUnimplementedPorts() {
    runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(ApprovalService.class));
  }

  @Test
  void enabledApprovalFailsFastWhenAnAdapterIsMissing() {
    runner.withPropertyValues("serms.approval.enabled=true")
        .withBean(ReservationApprovalPort.class, () -> mock(ReservationApprovalPort.class))
        .run(context -> {
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(
              org.springframework.beans.factory.NoSuchBeanDefinitionException.class);
          assertThat(context.getStartupFailure()).hasStackTraceContaining("ApprovalDecisionPort");
        });
  }

  @Test
  void enabledApprovalRegistersWithBothAdaptersWithoutScanOrderConditions() {
    runner.withPropertyValues("serms.approval.enabled=true")
        .withBean(ReservationApprovalPort.class, () -> mock(ReservationApprovalPort.class))
        .withBean(ApprovalDecisionPort.class, () -> mock(ApprovalDecisionPort.class))
        .run(context -> assertThat(context).hasNotFailed().hasSingleBean(ApprovalService.class));
  }
}
