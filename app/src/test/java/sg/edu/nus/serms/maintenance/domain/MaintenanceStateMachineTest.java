package sg.edu.nus.serms.maintenance.domain;

import static org.assertj.core.api.Assertions.*;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class MaintenanceStateMachineTest {
  private static final MaintenanceStateMachine MACHINE = new MaintenanceStateMachine();

  private record Transition(MaintenanceStatus current, MaintenanceAction action) {}

  private static final Map<Transition, MaintenanceStatus> LEGAL =
      Map.of(
          new Transition(MaintenanceStatus.REPORTED, MaintenanceAction.ASSIGN),
              MaintenanceStatus.ASSIGNED,
          new Transition(MaintenanceStatus.ASSIGNED, MaintenanceAction.START),
              MaintenanceStatus.IN_PROGRESS,
          new Transition(MaintenanceStatus.IN_PROGRESS, MaintenanceAction.RESOLVE),
              MaintenanceStatus.RESOLVED,
          new Transition(MaintenanceStatus.IN_PROGRESS, MaintenanceAction.MARK_NOT_REPAIRABLE),
              MaintenanceStatus.NOT_REPAIRABLE,
          new Transition(MaintenanceStatus.RESOLVED, MaintenanceAction.CLOSE),
              MaintenanceStatus.CLOSED,
          new Transition(MaintenanceStatus.NOT_REPAIRABLE, MaintenanceAction.CLOSE),
              MaintenanceStatus.CLOSED);

  static Stream<Arguments> legalTransitions() {
    return LEGAL.entrySet().stream()
        .map(entry ->
            Arguments.of(entry.getKey().current(), entry.getKey().action(), entry.getValue()));
  }

  static Stream<Arguments> illegalTransitions() {
    return Arrays.stream(MaintenanceStatus.values())
        .flatMap(
            current ->
                Arrays.stream(MaintenanceAction.values())
                    .map(action -> new Transition(current, action)))
        .filter(transition -> !LEGAL.containsKey(transition))
        .map(transition -> Arguments.of(transition.current(), transition.action()));
  }

  @ParameterizedTest
  @MethodSource("legalTransitions")
  void permitsOnlySpecifiedActions(
      MaintenanceStatus current, MaintenanceAction action, MaintenanceStatus expected) {
    assertThat(MACHINE.transition(current, action)).isEqualTo(expected);
  }

  @ParameterizedTest
  @MethodSource("illegalTransitions")
  void rejectsEveryOtherStateActionCombination(
      MaintenanceStatus current, MaintenanceAction action) {
    assertThatThrownBy(() -> MACHINE.transition(current, action))
        .isInstanceOf(InvalidMaintenanceTransitionException.class)
        .satisfies(
            failure -> {
              var transition = (InvalidMaintenanceTransitionException) failure;
              assertThat(transition.getCurrentStatus()).isEqualTo(current);
              assertThat(transition.getAttemptedAction()).isEqualTo(action);
              assertThat(transition.getMessage()).contains(current.name(), action.name());
            });
  }

  @ParameterizedTest
  @EnumSource(MaintenanceAction.class)
  void closedIsTerminal(MaintenanceAction action) {
    assertThatThrownBy(() -> MACHINE.transition(MaintenanceStatus.CLOSED, action))
        .isInstanceOf(InvalidMaintenanceTransitionException.class);
  }

  @Test
  void rejectsNullInputsImmediately() {
    assertThatNullPointerException()
        .isThrownBy(() -> MACHINE.transition(null, MaintenanceAction.ASSIGN))
        .withMessage("current status");
    assertThatNullPointerException()
        .isThrownBy(() -> MACHINE.transition(MaintenanceStatus.REPORTED, null))
        .withMessage("maintenance action");
  }
}
