package it.bollettalab.platform;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration
public class PlatformConfiguration {
  @Bean public InternalClient internalClient(@Value("${platform.internal-key:}") String key, Environment env) {
    if (key.length() < 32 && !env.matchesProfiles("test")) throw new IllegalStateException("Esegui scripts/start.sh per generare la configurazione sicura");
    return new InternalClient(key);
  }
  @Bean @ConditionalOnMissingBean(IdentityProvider.class)
  public IdentityProvider remoteIdentity(InternalClient client, @Value("${platform.users-url:http://utenti:8080}") String users) {
    return req -> client.get(users + "/internal/identity", req.getHeader("Cookie") == null ? "" : req.getHeader("Cookie"), Identity.class);
  }
  @Bean public FilterRegistrationBean<PlatformFilter> platformFilter(IdentityProvider identities, InternalClient client, Environment env) {
    var registration = new FilterRegistrationBean<>(new PlatformFilter(identities, client, env));
    registration.setOrder(-100); return registration;
  }
  @Bean public PlatformErrors platformErrors() { return new PlatformErrors(); }
}
