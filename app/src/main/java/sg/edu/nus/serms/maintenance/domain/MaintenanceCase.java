package sg.edu.nus.serms.maintenance.domain;

import java.util.ArrayList;
import java.util.List;

public final class MaintenanceCase {
  private static final MaintenanceStateMachine TRANSITIONS = new MaintenanceStateMachine();

  private MaintenanceStatus status = MaintenanceStatus.REPORTED;
  private String diagnosis;
  private String repairAction;
  private final List<String> maintenanceNotes = new ArrayList<>();
  private String completionInformation;

  public MaintenanceStatus status() {
    return status;
  }

  public String diagnosis() {
    return diagnosis;
  }

  public String repairAction() {
    return repairAction;
  }

  public List<String> maintenanceNotes() {
    return List.copyOf(maintenanceNotes);
  }

  public String completionInformation() {
    return completionInformation;
  }

  public void assign() {
    status = TRANSITIONS.transition(status, MaintenanceAction.ASSIGN);
  }

  public void start() {
    status = TRANSITIONS.transition(status, MaintenanceAction.START);
  }

  public void recordDiagnosis(String diagnosis) {
    requireWorkInProgress();
    this.diagnosis = requireText(diagnosis);
  }

  public void recordRepairAction(String repairAction) {
    requireWorkInProgress();
    this.repairAction = requireText(repairAction);
  }

  public void recordMaintenanceNotes(String note) {
    requireWorkInProgress();
    maintenanceNotes.add(requireText(note));
  }

  public void resolve(String completionInformation) {
    complete(MaintenanceAction.RESOLVE, completionInformation);
  }

  public void markNotRepairable(String completionInformation) {
    complete(MaintenanceAction.MARK_NOT_REPAIRABLE, completionInformation);
  }

  public void close() {
    status = TRANSITIONS.transition(status, MaintenanceAction.CLOSE);
  }

  private void complete(MaintenanceAction action, String information) {
    MaintenanceStatus next = TRANSITIONS.transition(status, action);
    String text = requireText(information);
    completionInformation = text;
    status = next;
  }

  private void requireWorkInProgress() {
    if (status != MaintenanceStatus.IN_PROGRESS) {
      throw new IllegalStateException("Maintenance work requires IN_PROGRESS status; current: " + status);
    }
  }

  private static String requireText(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Maintenance information must not be blank");
    }
    return value.strip();
  }
}
