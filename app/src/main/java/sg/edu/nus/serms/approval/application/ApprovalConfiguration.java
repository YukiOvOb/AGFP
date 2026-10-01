package sg.edu.nus.serms.approval.application;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sg.edu.nus.serms.approval.domain.ApprovalPolicyChain;
import sg.edu.nus.serms.approval.domain.DecisionCommentHandler;
import sg.edu.nus.serms.approval.domain.NoSelfApprovalHandler;

/** Wires approval only when its collaborating module adapters are available. */
@Configuration(proxyBeanMethods = false)
public class ApprovalConfiguration {

  @Bean
  ApprovalPolicyChain approvalPolicyChain() {
    return new ApprovalPolicyChain(
        List.of(new NoSelfApprovalHandler(), new DecisionCommentHandler()));
  }

  @Bean
  @ConditionalOnBean({ReservationApprovalPort.class, ApprovalDecisionPort.class})
  ApprovalService approvalService(
      ReservationApprovalPort reservations,
      ApprovalDecisionPort decisions,
      ApprovalPolicyChain policyChain,
      ApplicationEventPublisher events) {
    return new ApprovalService(reservations, decisions, policyChain, events);
  }
}
