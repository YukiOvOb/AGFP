package sg.edu.nus.serms.maintenance.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;
import sg.edu.nus.serms.maintenance.domain.*;
import sg.edu.nus.serms.maintenance.repository.*;

/** Checks the application boundary independently of HTTP and Spring role interception. */
class AssignedTechnicianEnforcementTest {
  static final UUID CASE = new UUID(0, 1), EQUIPMENT = new UUID(0, 2), A = new UUID(0, 3), B = new UUID(0, 4);
  static final Instant NOW = Instant.parse("2026-10-06T00:00:00Z");
  final MaintenanceCaseRepository cases = mock(MaintenanceCaseRepository.class);
  final MaintenanceHistoryRepository history = mock(MaintenanceHistoryRepository.class);
  final EquipmentMaintenancePort equipment = mock(EquipmentMaintenancePort.class);
  final MaintenanceEventPublisher events = mock(MaintenanceEventPublisher.class);
  final MaintenanceService service = new MaintenanceService(cases, history, equipment,
      mock(TechnicianDirectoryPort.class), events, () -> CASE, Clock.fixed(NOW, ZoneOffset.UTC));

  MaintenanceCase item(String action, UUID technician) {
    var item = new MaintenanceCase(CASE, EQUIPMENT, A, null, "Fault", NOW);
    item.assign(technician);
    if (!action.equals("start")) item.start();
    return item;
  }

  void act(String action, UUID actor) {
    switch (action) {
      case "start" -> service.startMaintenance(CASE, actor);
      case "diagnosis" -> service.recordDiagnosis(CASE, "Diagnosis", actor);
      case "repair" -> service.recordRepairAction(CASE, "Repair", actor);
      case "note" -> service.addMaintenanceNote(CASE, "Note", actor);
      case "resolve" -> service.resolve(CASE, "Repaired", actor);
      case "unrepairable" -> service.markUnrepairable(CASE, "No parts", actor);
      default -> throw new AssertionError(action);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"start", "diagnosis", "repair", "note", "resolve", "unrepairable"})
  void anotherTechnicianCannotMutateOrPublish(String action) {
    when(cases.findById(CASE)).thenReturn(Optional.of(item(action, A)));
    assertThatThrownBy(() -> act(action, B)).isInstanceOf(AccessDeniedException.class);
    var order = inOrder(cases, equipment);
    order.verify(cases).findById(CASE);
    order.verify(equipment).lockForMaintenance(EQUIPMENT);
    order.verify(cases).findById(CASE);
    verify(cases, never()).update(any());
    verifyNoInteractions(history, events);
    verify(equipment, never()).markUnderMaintenance(any());
    verify(equipment, never()).markRetired(any());
    verify(equipment, never()).recomputeAfterResolvedMaintenance(any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"start", "diagnosis", "repair", "note", "resolve", "unrepairable"})
  void authorizationUsesPostLockReloadNotEarlierAssignment(String action) {
    when(cases.findById(CASE)).thenReturn(Optional.of(item(action, B)), Optional.of(item(action, A)));
    assertThatThrownBy(() -> act(action, B)).isInstanceOf(AccessDeniedException.class);
    verify(cases, times(2)).findById(CASE);
    verify(cases, never()).update(any());
    verifyNoInteractions(history, events);
    verify(equipment).lockForMaintenance(EQUIPMENT);
    verifyNoMoreInteractions(equipment);
  }

  @ParameterizedTest
  @ValueSource(strings = {"start", "diagnosis", "repair", "note", "resolve", "unrepairable"})
  void newlyAssignedActorUsesReloadedStateBeforeSuccessfulMutation(String action) {
    when(cases.findById(CASE)).thenReturn(Optional.of(item(action, A)), Optional.of(item(action, B)));
    when(cases.update(any())).thenAnswer(call -> call.<MaintenanceCase>getArgument(0).copy());
    act(action, B);
    var order = inOrder(cases, equipment, history);
    order.verify(cases).findById(CASE);
    order.verify(equipment).lockForMaintenance(EQUIPMENT);
    order.verify(cases).findById(CASE);
    if (Set.of("start", "resolve", "unrepairable").contains(action)) order.verify(cases).update(any());
    if (action.equals("resolve")) order.verify(equipment).recomputeAfterResolvedMaintenance(EQUIPMENT);
    if (action.equals("unrepairable")) order.verify(equipment).markRetired(EQUIPMENT);
    order.verify(history).append(argThat(entry -> entry.actorId().equals(B)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"start", "diagnosis", "repair", "note", "resolve", "unrepairable"})
  void unassignedPostLockCaseIsDenied(String action) {
    var open = new MaintenanceCase(CASE, EQUIPMENT, A, null, "Fault", NOW);
    when(cases.findById(CASE)).thenReturn(Optional.of(item(action, A)), Optional.of(open));
    assertThatThrownBy(() -> act(action, A)).isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(history, events);
    verify(cases, never()).update(any());
    verify(equipment).lockForMaintenance(EQUIPMENT);
    verifyNoMoreInteractions(equipment);
  }
}
