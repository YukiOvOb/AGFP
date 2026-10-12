package sg.edu.nus.serms.loan.domain;

/** State objects are enum singletons; shared possession/maintenance facts stay in the service. */
public enum EquipmentLoanState {
  AVAILABLE { @Override public boolean permitsCheckout() { return true; } },
  ON_LOAN, UNDER_MAINTENANCE, RETIRED;

  public boolean permitsCheckout() { return false; }

  public EquipmentLoanState afterReturn(boolean activeMaintenance, boolean activeLoan) {
    if (this == RETIRED) return RETIRED;
    if (activeMaintenance) return UNDER_MAINTENANCE;
    return activeLoan ? ON_LOAN : AVAILABLE;
  }
}
