package sg.edu.nus.serms.loan.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sg.edu.nus.serms.loan.domain.LoanView;
import sg.edu.nus.serms.loan.domain.CheckoutCandidate;
import sg.edu.nus.serms.loan.service.LoanService;
import tools.jackson.databind.annotation.JsonDeserialize;
import sg.edu.nus.serms.shared.api.StrictVersionDeserializer;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1/loans")
public class LoanController {
  private final LoanService service;
  public LoanController(LoanService service) { this.service = service; }
  public record CheckoutRequest(@NotNull UUID reservationId, @NotNull UUID borrowerId,
      @NotNull @PositiveOrZero @JsonDeserialize(using=StrictVersionDeserializer.class) Long expectedVersion, @AssertTrue boolean identityConfirmed) {}
  public record ReturnRequest(@NotNull @PositiveOrZero @JsonDeserialize(using=StrictVersionDeserializer.class) Long expectedVersion,
      @NotBlank @Pattern(regexp="GOOD|DAMAGED") String condition, @Size(max=2000) String note) {}

  @GetMapping
  @Operation(operationId="listLoans")
  public List<LoanView> list(@RequestParam(defaultValue="true") boolean activeOnly,
      @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size,
      @RequestParam(required=false) String q) {
    return service.list(activeOnly,page,size,q);
  }
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(operationId="checkoutEquipment")
  public ResponseEntity<LoanView> checkout(@Valid @RequestBody CheckoutRequest body) {
    var loan = service.checkout(body.reservationId(),body.borrowerId(),body.expectedVersion(),body.identityConfirmed());
    return ResponseEntity.created(URI.create("/api/v1/loans/" + loan.loanId())).body(loan);
  }
  @GetMapping("/checkouts")
  @Operation(operationId="listCheckoutQueue")
  public List<CheckoutCandidate> checkoutQueue(@RequestParam(defaultValue="0") int page,
      @RequestParam(defaultValue="20") int size, @RequestParam(required=false) String q) {
    return service.checkoutQueue(page,size,q);
  }
  @PostMapping("/{loanId}/return")
  @Operation(operationId="receiveLoanReturn")
  public LoanView receive(@PathVariable UUID loanId, @Valid @RequestBody ReturnRequest body) {
    return service.receive(loanId,body.expectedVersion(),body.condition(),body.note());
  }
  @GetMapping("/{loanId}")
  @Operation(operationId="getLoan")
  public LoanView get(@PathVariable UUID loanId) { return service.get(loanId); }
}
