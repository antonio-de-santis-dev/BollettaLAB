package it.progettoluce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.springframework.data.jpa.repository.config.EnableJpaRepositories(
    basePackages = "it.progettoluce",
    repositoryBaseClass = it.bollettalab.platform.ScopedJpaRepository.class)
@SpringBootApplication(scanBasePackages = {"it.progettoluce", "it.bollettalab.platform"})
public class LuceApplication {
  public static void main(String[] args) {
    SpringApplication.run(LuceApplication.class, args);
  }
}
