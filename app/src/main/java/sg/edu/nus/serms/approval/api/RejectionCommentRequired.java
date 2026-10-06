package sg.edu.nus.serms.approval.api;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import sg.edu.nus.serms.approval.domain.ApprovalDecisionType;

/** Conditional validation is attached to comment so the shared errors[] format remains useful. */
@Target(TYPE)
@Retention(RUNTIME)
@Constraint(validatedBy = RejectionCommentRequired.Validator.class)
public @interface RejectionCommentRequired {
  String message() default "A rejection reason is required.";
  Class<?>[] groups() default {};
  Class<? extends Payload>[] payload() default {};

  final class Validator
      implements ConstraintValidator<RejectionCommentRequired, ApprovalApiDtos.DecisionRequest> {
    @Override
    public boolean isValid(
        ApprovalApiDtos.DecisionRequest request, ConstraintValidatorContext context) {
      if (request == null || request.decision() != ApprovalDecisionType.REJECTED
          || (request.comment() != null && !request.comment().isBlank())) return true;
      context.disableDefaultConstraintViolation();
      context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
          .addPropertyNode("comment").addConstraintViolation();
      return false;
    }
  }
}
