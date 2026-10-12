package it.bollettalab.utenti;

import static org.assertj.core.api.Assertions.*;

import it.bollettalab.platform.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.*;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:users;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "platform.internal-key=01234567890123456789012345678901",
      "platform.cookie-secure=false"
    })
@ActiveProfiles("test")
class AccountsTest {
  @Autowired Accounts accounts;

  @AfterEach
  void clear() {
    RequestContext.clear();
  }

  @Test
  void unknownAccountsAnswerWithHttpErrorsInsteadOfServerErrors() {
    assertThatThrownBy(() -> accounts.account("missing"))
        .isInstanceOfSatisfying(HttpProblem.class, e -> assertThat(e.status()).isEqualTo(404));
    assertThatThrownBy(
            () ->
                accounts.login(
                    Map.of("email", "nessuno-" + UUID.randomUUID() + "@test.it", "password", "x"),
                    new MockHttpServletRequest(),
                    new MockHttpServletResponse()))
        .isInstanceOfSatisfying(HttpProblem.class, e -> assertThat(e.status()).isEqualTo(401));
  }

  @Test
  void throttlingIsSeparatePerAction() {
    String ip = "203.0.113." + new Random().nextInt(250);
    for (int n = 0; n < 10; n++) accounts.throttle("register", ip, 10);
    assertThatThrownBy(() -> accounts.throttle("register", ip, 10))
        .isInstanceOfSatisfying(HttpProblem.class, e -> assertThat(e.status()).isEqualTo(429));
    accounts.throttle(ip);
  }

  @Test
  void developmentProfileOnlyOnLoopbackAddresses() {
    assertThat(Accounts.loopback("http://localhost:8091")).isTrue();
    assertThat(Accounts.loopback("http://127.0.0.1:8091")).isTrue();
    assertThat(Accounts.loopback("https://bollettalab.it")).isFalse();
    assertThat(Accounts.loopback("")).isFalse();
    var env =
        new org.springframework.mock.env.MockEnvironment()
            .withProperty("platform.public-url", "https://bollettalab.it");
    env.setActiveProfiles("dev");
    assertThatThrownBy(() -> new Accounts(accounts.jdbc(), env, null))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void registrationLoginAndBlockedSession() {
    String email = "privato-" + UUID.randomUUID() + "@test.it";
    accounts.register(
        Map.of("email", email, "password", "Password123!", "name", "Mario", "type", "PRIVATE"));
    var response = new MockHttpServletResponse();
    Identity identity =
        accounts.login(
            Map.of("email", email, "password", "Password123!"),
            new MockHttpServletRequest(),
            response);
    assertThat(identity.role()).isEqualTo("PRIVATE");
    assertThat(identity.status()).isEqualTo("PENDING_PAYMENT");
    String raw = response.getHeader("Set-Cookie").split(";")[0].split("=")[1];
    var request = new MockHttpServletRequest();
    request.setCookies(new jakarta.servlet.http.Cookie("BL_SESSION", raw));
    assertThat(accounts.authenticate(request).id()).isEqualTo(identity.id());
    accounts.jdbc().update("UPDATE accounts SET status='BLOCKED' WHERE id=?", identity.id());
    assertThatThrownBy(() -> accounts.authenticate(request)).isInstanceOf(HttpProblem.class);
  }

  @Test
  void companyAllowsExactlyTwoInvitationsAndResetRevokesSessions() {
    String email = "azienda-" + UUID.randomUUID() + "@test.it";
    accounts.register(
        Map.of(
            "email",
            email,
            "password",
            "Password123!",
            "name",
            "Titolare",
            "type",
            "COMPANY",
            "companyName",
            "Impresa",
            "vatNumber",
            "12345678901",
            "address",
            "Via Roma 1"));
    var owner =
        accounts.login(
            Map.of("email", email, "password", "Password123!"),
            new MockHttpServletRequest(),
            new MockHttpServletResponse());
    RequestContext.set(owner);
    accounts.invite(Map.of("email", UUID.randomUUID() + "@test.it", "name", "Agente 1"), 2);
    accounts.invite(Map.of("email", UUID.randomUUID() + "@test.it", "name", "Agente 2"), 2);
    assertThatThrownBy(
            () ->
                accounts.invite(
                    Map.of("email", UUID.randomUUID() + "@test.it", "name", "Agente 3"), 2))
        .hasMessageContaining("esauriti");
    var result = accounts.forgot(Map.of("email", email));
    String token = result.get("resetUrl").toString().split("token=")[1];
    accounts.reset(Map.of("token", token, "password", "NuovaPassword123!"));
    assertThat(
            accounts
                .jdbc()
                .queryForObject(
                    "SELECT COUNT(*) FROM sessions WHERE user_id=?", Integer.class, owner.id()))
        .isZero();
    assertThatThrownBy(
            () -> accounts.reset(Map.of("token", token, "password", "NuovaPassword123!")))
        .isInstanceOf(HttpProblem.class);
  }
}
