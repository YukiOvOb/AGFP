package sg.edu.nus.serms.loan.domain;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class EquipmentLoanStateTest {
  @Test void checkoutOnlyFromAvailable() {
    for(var state:EquipmentLoanState.values())
      assertThat(state.permitsCheckout()).isEqualTo(state==EquipmentLoanState.AVAILABLE);
  }
  @Test void retiredAndMaintenanceTakePrecedenceOverPossession() {
    assertThat(EquipmentLoanState.RETIRED.afterReturn(true,true)).isEqualTo(EquipmentLoanState.RETIRED);
    assertThat(EquipmentLoanState.ON_LOAN.afterReturn(true,false)).isEqualTo(EquipmentLoanState.UNDER_MAINTENANCE);
    assertThat(EquipmentLoanState.UNDER_MAINTENANCE.afterReturn(false,true)).isEqualTo(EquipmentLoanState.ON_LOAN);
    assertThat(EquipmentLoanState.ON_LOAN.afterReturn(false,false)).isEqualTo(EquipmentLoanState.AVAILABLE);
  }
}
