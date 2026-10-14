package sg.edu.nus.serms.identity.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/** Runs the first-admin bootstrap once per start; without settings it does nothing at all. */
@Component
@EnableConfigurationProperties(FirstAdminProperties.class)
class FirstAdminBootstrapRunner implements ApplicationRunner {
  private final FirstAdminBootstrap bootstrap;
  private final FirstAdminProperties settings;

  FirstAdminBootstrapRunner(FirstAdminBootstrap bootstrap, FirstAdminProperties settings) {
    this.bootstrap = bootstrap;
    this.settings = settings;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (settings.requested()) bootstrap.run(settings);
  }
}
