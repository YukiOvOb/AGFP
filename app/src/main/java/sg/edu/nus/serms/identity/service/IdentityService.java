package sg.edu.nus.serms.identity.service;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.serms.identity.domain.UserAccount;
import sg.edu.nus.serms.identity.repository.UserAccountRepository;

@Service
@Transactional(readOnly = true)
public class IdentityService {
  private final UserAccountRepository accounts;

  public IdentityService(UserAccountRepository accounts) {
    this.accounts = accounts;
  }

  public Optional<UserAccount> findByEmail(String email) {
    return accounts.findByEmail(email.strip().toLowerCase(Locale.ROOT));
  }

  public Optional<UserAccount> findById(UUID id) {
    return accounts.findById(id);
  }
}
