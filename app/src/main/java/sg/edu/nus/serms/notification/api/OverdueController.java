package sg.edu.nus.serms.notification.api;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sg.edu.nus.serms.notification.service.OverdueLoanService;

@RestController
@RequestMapping("/api/v1/overdue")
public class OverdueController {
  private final OverdueLoanService service;
  public OverdueController(OverdueLoanService service) { this.service=service; }
  @GetMapping
  @Operation(operationId="listOverdueLoans")
  public OverdueLoanService.OverduePage list(@RequestParam(defaultValue="0") int page,
      @RequestParam(defaultValue="20") int size,
      @RequestParam(defaultValue="desc") String order) { return service.list(page,size,order); }
}
