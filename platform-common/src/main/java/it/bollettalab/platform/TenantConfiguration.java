package it.bollettalab.platform;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.*;

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "platform.simulator",
    havingValue = "true")
@Configuration
public class TenantConfiguration {
  @Bean
  HibernatePropertiesCustomizer tenants() {
    return p ->
        p.put(
            "hibernate.tenant_identifier_resolver",
            new CurrentTenantIdentifierResolver<String>() {
              public String resolveCurrentTenantIdentifier() {
                return RequestContext.workspace();
              }

              public boolean validateExistingCurrentSessions() {
                return true;
              }
            });
  }
}
