package it.bollettalab.platform;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@org.springframework.scheduling.annotation.EnableScheduling
@Configuration
public class PlatformConfiguration {
  @Bean
  public InternalClient internalClient(
      @Value("${platform.internal-key:}") String key, Environment env) {
    if (key.length() < 32 && !env.matchesProfiles("test"))
      throw new IllegalStateException(
          "Esegui scripts/start.sh per generare la configurazione sicura");
    return new InternalClient(key);
  }

  @Bean
  @ConditionalOnMissingBean(IdentityProvider.class)
  public IdentityProvider remoteIdentity(
      InternalClient client, @Value("${platform.users-url:http://utenti:8080}") String users) {
    return req ->
        client.get(
            users + "/internal/identity",
            req.getHeader("Cookie") == null ? "" : req.getHeader("Cookie"),
            Identity.class);
  }

  @Bean
  @ConditionalOnMissingBean(ActivitySink.class)
  public ActivitySink remoteActivities(
      Environment env,
      org.springframework.jdbc.core.JdbcTemplate db,
      com.fasterxml.jackson.databind.ObjectMapper json) {
    if (env.matchesProfiles("test")) return event -> {};
    return new ActivityOutbox(
        db,
        json,
        env.getProperty("platform.users-url", "http://utenti:8080"),
        env.getProperty("platform.internal-key", ""));
  }

  @Bean
  public FilterRegistrationBean<PlatformFilter> platformFilter(
      IdentityProvider identities,
      InternalClient client,
      Environment env,
      ActivitySink activities) {
    var registration =
        new FilterRegistrationBean<>(new PlatformFilter(identities, client, env, activities));
    registration.setOrder(-100);
    return registration;
  }

  @Bean
  public PlatformErrors platformErrors() {
    return new PlatformErrors();
  }
}
