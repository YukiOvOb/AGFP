package sg.edu.nus.serms.approval.service;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sg.edu.nus.serms.approval.domain.ApprovalPolicyChain;
import sg.edu.nus.serms.approval.domain.DecisionCommentHandler;
import sg.edu.nus.serms.approval.domain.NoSelfApprovalHandler;

/** Explicit activation avoids bean-registration-order conditions. Missing enabled ports fail fast. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "serms.approval.enabled", havingValue = "true")
public class ApprovalConfiguration {

  @Bean
  ApprovalPolicyChain approvalPolicyChain() {
    return new ApprovalPolicyChain(
        List.of(new NoSelfApprovalHandler(), new DecisionCommentHandler()));
  }

  @Bean
  ApprovalService approvalService(
      ReservationApprovalPort reservations,
      ApprovalDecisionPort decisions,
      ApprovalPolicyChain policyChain,
      ApplicationEventPublisher events) {
    return new ApprovalService(reservations, decisions, policyChain, events);
  }
}
