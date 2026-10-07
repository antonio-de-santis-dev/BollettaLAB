package it.progettogas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.springframework.data.jpa.repository.config.EnableJpaRepositories(
    basePackages = "it.progettogas",
    repositoryBaseClass = it.bollettalab.platform.ScopedJpaRepository.class)
@SpringBootApplication(scanBasePackages = {"it.progettogas", "it.bollettalab.platform"})
public class GasApplication {
  public static void main(String[] args) {
    SpringApplication.run(GasApplication.class, args);
  }
}
