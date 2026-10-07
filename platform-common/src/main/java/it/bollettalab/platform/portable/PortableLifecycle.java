package it.bollettalab.platform.portable;

import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Internal routes already protected by PlatformFilter's internal key. */
@RestController
@ConditionalOnProperty(name = "platform.portable-enabled", havingValue = "true")
public class PortableLifecycle {
  private final ConfigurableApplicationContext context;
  private final Environment env;

  public PortableLifecycle(ConfigurableApplicationContext context, Environment env) {
    this.context = context;
    this.env = env;
  }

  @GetMapping("/internal/portable/status")
  public Map<String, String> status() {
    return Map.of(
        "instance",
        env.getRequiredProperty("PORTABLE_INSTANCE"),
        "service",
        env.getRequiredProperty("platform.service"));
  }

  @PostMapping("/internal/portable/shutdown")
  public Map<String, Boolean> stop() {
    Thread shutdown =
        new Thread(
            () -> {
              try {
                Thread.sleep(300);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              context.close();
            },
            "portable-service-stop");
    shutdown.start();
    return Map.of("stopping", true);
  }
}
