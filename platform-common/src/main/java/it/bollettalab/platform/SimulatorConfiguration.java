package it.bollettalab.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "platform.simulator",
    havingValue = "true")
@Configuration
@EnableScheduling
@Import(TenantConfiguration.class)
public class SimulatorConfiguration {
  @Bean
  FilterRegistrationBean<CreditFilter> credits(
      InternalClient c, LocalReceipts r, Environment e, ObjectMapper j) {
    var b = new FilterRegistrationBean<>(new CreditFilter(c, r, e, j));
    b.setOrder(-90);
    return b;
  }
}
