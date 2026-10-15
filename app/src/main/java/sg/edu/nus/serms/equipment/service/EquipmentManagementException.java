package sg.edu.nus.serms.equipment.service;

public class EquipmentManagementException extends RuntimeException {
  private final int status;
  private final String field;

  public EquipmentManagementException(int status, String field, String message) {
    super(message);
    this.status = status;
    this.field = field;
  }

  public int status() { return status; }
  public String field() { return field; }
}
