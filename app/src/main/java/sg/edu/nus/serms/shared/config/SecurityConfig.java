package sg.edu.nus.serms.shared.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.*;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.session.*;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.savedrequest.NullRequestCache;
import sg.edu.nus.serms.shared.security.*;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    var p = (DelegatingPasswordEncoder) PasswordEncoderFactories.createDelegatingPasswordEncoder();
    p.setDefaultPasswordEncoderForMatches(new BCryptPasswordEncoder());
    return p;
  }

  @Bean
  AuthenticationManager authenticationManager(IdentityAuthenticationProvider provider) {
    return new ProviderManager(provider);
  }

  @Bean
  CookieCsrfTokenRepository csrfRepository(
      @Value("${serms.security.secure-cookies:true}") boolean secure) {
    var r = CookieCsrfTokenRepository.withHttpOnlyFalse();
    r.setCookieCustomizer(c -> c.path("/").sameSite("Lax").secure(secure));
    return r;
  }

  @Bean
  SecurityContextRepository contextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  SessionAuthenticationStrategy sessionStrategy(CookieCsrfTokenRepository csrf) {
    return new CompositeSessionAuthenticationStrategy(
        List.of(new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrf)));
  }

  @Bean
  SecurityFilterChain security(
      HttpSecurity http,
      CookieCsrfTokenRepository csrf,
      SecurityContextRepository contexts,
      SecurityProblemWriter problems,
      @Value("${springdoc.swagger-ui.enabled:false}") boolean docs)
      throws Exception {
    // The SPA sends the raw Cookie token in a header; no server-rendered token appears in HTML.
    var handler = new CsrfTokenRequestAttributeHandler();
    http.csrf(c -> c.csrfTokenRepository(csrf).csrfTokenRequestHandler(handler))
        .securityContext(c -> c.securityContextRepository(contexts).requireExplicitSave(true))
        .requestCache(c -> c.requestCache(new NullRequestCache()))
        .authorizeHttpRequests(
            a -> {
              a.requestMatchers(
                      "/api/v1/auth/csrf",
                      "/api/v1/auth/login",
                      "/api/v1/auth/logout",
                      "/api/v1/health",
                      "/error")
                  .permitAll();
              if (docs)
                a.requestMatchers("/api/v1/openapi.json/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll();
              a.anyRequest().authenticated();
            })
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, error) ->
                            problems.write(res, 401, "unauthenticated", "Sign in to continue."))
                    .accessDeniedHandler(
                        (req, res, error) ->
                            problems.write(
                                res,
                                403,
                                error instanceof CsrfException ? "csrf" : "forbidden",
                                error instanceof CsrfException
                                    ? "Refresh your session and try again."
                                    : "You do not have permission for this action.")))
        .formLogin(f -> f.disable())
        .httpBasic(b -> b.disable())
        .logout(l -> l.disable());
    return http.build();
  }
}
