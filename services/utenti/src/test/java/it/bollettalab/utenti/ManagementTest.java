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
      "spring.datasource.url=jdbc:h2:mem:management;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "platform.internal-key=01234567890123456789012345678901",
      "platform.cookie-secure=false"
    })
@ActiveProfiles("test")
class ManagementTest {
  @Autowired Accounts accounts;
  @Autowired Activities activities;
  @Autowired UsersApi api;

  @AfterEach
  void clear() {
    RequestContext.clear();
  }

  Identity owner() {
    String email = "owner-" + UUID.randomUUID() + "@test.it";
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
            "Impresa Test",
            "vatNumber",
            "12345678901",
            "address",
            "Via Roma 1"));
    var owner =
        accounts.login(
            Map.of("email", email, "password", "Password123!"),
            new MockHttpServletRequest(),
            new MockHttpServletResponse());
    accounts.jdbc().update("UPDATE workspaces SET status='ACTIVE' WHERE id=?", owner.workspaceId());
    RequestContext.set(owner);
    return owner;
  }

  @Test
  void directAgentCreationOccupiesOneSeatAndResetRevokesSessions() {
    var owner = owner();
    String email = "agent-" + UUID.randomUUID() + "@test.it";
    var created = accounts.createAgent(Map.of("name", "Agente Test", "email", email), 1);
    String password = created.get("temporaryPassword").toString();
    assertThat(password).hasSizeGreaterThanOrEqualTo(10);
    var agent =
        accounts.login(
            Map.of("email", email, "password", password),
            new MockHttpServletRequest(),
            new MockHttpServletResponse());
    assertThat(agent.workspaceId()).isEqualTo(owner.workspaceId());
    assertThat(agent.role()).isEqualTo("AGENT");
    assertThat(
            accounts
                .jdbc()
                .queryForObject(
                    "SELECT password_hash FROM accounts WHERE id=?", String.class, agent.id()))
        .isNotEqualTo(password);
    assertThatThrownBy(
            () ->
                accounts.createAgent(
                    Map.of("name", "Altro", "email", "other-" + UUID.randomUUID() + "@test.it"), 1))
        .isInstanceOf(HttpProblem.class);
    var reset = accounts.resetAgent(agent.id());
    assertThat(reset.get("temporaryPassword")).isNotEqualTo(password);
    assertThat(
            accounts
                .jdbc()
                .queryForObject(
                    "SELECT COUNT(*) FROM sessions WHERE user_id=?", Integer.class, agent.id()))
        .isZero();
    assertThatThrownBy(
            () ->
                accounts.login(
                    Map.of("email", email, "password", password),
                    new MockHttpServletRequest(),
                    new MockHttpServletResponse()))
        .isInstanceOf(HttpProblem.class);
  }

  @Test
  void pendingInviteConversionDoesNotOccupyTwoSeats() {
    owner();
    String email = "pending-" + UUID.randomUUID() + "@test.it";
    accounts.invite(Map.of("name", "In Attesa", "email", email), 1);
    var direct = accounts.createAgent(Map.of("name", "In Attesa", "email", email), 1);
    assertThat(direct).containsKey("temporaryPassword");
    assertThat(
            accounts
                .jdbc()
                .queryForObject(
                    "SELECT COUNT(*) FROM account_tokens WHERE email=? AND purpose='INVITE'",
                    Integer.class,
                    email))
        .isZero();
  }

  @Test
  void ownerCannotResetAnotherCompaniesAgent() {
    owner();
    var created =
        accounts.createAgent(
            Map.of("name", "Agente", "email", "agent-" + UUID.randomUUID() + "@test.it"), 2);
    var agent =
        accounts.login(
            Map.of("email", created.get("email"), "password", created.get("temporaryPassword")),
            new MockHttpServletRequest(),
            new MockHttpServletResponse());
    owner();
    assertThatThrownBy(() -> accounts.resetAgent(agent.id()))
        .isInstanceOfSatisfying(HttpProblem.class, e -> assertThat(e.status()).isEqualTo(404));
  }

  @Test
  void profileChangesAreScopedAndRequireCurrentPasswordForSensitiveData() {
    var owner = owner();
    var form = new HashMap<String, Object>(accounts.profile());
    form.put("name", "Nuovo Nome");
    accounts.updateProfile(form);
    assertThat(accounts.profile().get("name")).isEqualTo("Nuovo Nome");
    form.put("newPassword", "NewPassword123!");
    assertThatThrownBy(() -> accounts.updateProfile(form)).isInstanceOf(HttpProblem.class);
    form.put("currentPassword", "Password123!");
    accounts.updateProfile(form);
    assertThat(
            accounts
                .jdbc()
                .queryForObject(
                    "SELECT COUNT(*) FROM sessions WHERE user_id=?", Integer.class, owner.id()))
        .isZero();
    assertThat(
            accounts
                .login(
                    Map.of("email", owner.email(), "password", "NewPassword123!"),
                    new MockHttpServletRequest(),
                    new MockHttpServletResponse())
                .id())
        .isEqualTo(owner.id());
  }

  @Test
  void verificationLinkOfThePreviousEmailCannotVerifyTheNewOne() {
    String email = "private-" + UUID.randomUUID() + "@test.it";
    var registered =
        accounts.register(
            Map.of(
                "email", email, "password", "Password123!", "name", "Privato", "type", "PRIVATE"));
    String oldLink = registered.get("verificationUrl").toString();
    var user =
        accounts.login(
            Map.of("email", email, "password", "Password123!"),
            new MockHttpServletRequest(),
            new MockHttpServletResponse());
    RequestContext.set(user);
    var form = new HashMap<String, Object>(accounts.profile());
    form.put("email", "new-" + email);
    form.put("currentPassword", "Password123!");
    accounts.updateProfile(form);
    assertThatThrownBy(() -> accounts.verify(oldLink.split("token=")[1]))
        .isInstanceOfSatisfying(HttpProblem.class, e -> assertThat(e.status()).isEqualTo(400));
    assertThat(
            accounts
                .jdbc()
                .queryForObject(
                    "SELECT verified FROM accounts WHERE id=?", Boolean.class, user.id()))
        .isFalse();
  }

  @Test
  void activityDeliveryIsIdempotent() {
    var event =
        new ActivityEvent(
            null,
            null,
            "Sistema",
            "",
            "Test",
            "Evento ritentato",
            "COMPLETED",
            "gas",
            null,
            "POST",
            "/api/confronti",
            201);
    activities.record(event);
    activities.record(event);
    assertThat(
            accounts
                .jdbc()
                .queryForObject(
                    "SELECT COUNT(*) FROM audit_log WHERE id=?", Integer.class, event.eventId()))
        .isEqualTo(1);
  }

  @Test
  void batchWithOverLongOrInvalidEventsDoesNotBlockTheQueue() {
    var unmatchedPath =
        new ActivityEvent(
            null,
            null,
            null,
            "",
            "Consultazione · operazione account",
            "Consultazione · luce · HTTP 401",
            "WARNING",
            "luce",
            null,
            "GET",
            "/api/" + "a".repeat(400),
            401);
    var invalid =
        new ActivityEvent(
            null, null, null, "", "Test", "Stato sconosciuto", "DONE",
            "gas", null, "GET", "/", 200);
    var valid =
        new ActivityEvent(
            null, null, null, "", "Test", "Evento valido", "COMPLETED",
            "gas", null, "GET", "/", 200);
    @SuppressWarnings("unchecked")
    var answer = (Map<String, Object>) api.activities(List.of(unmatchedPath, invalid, valid));
    assertThat(answer).containsEntry("recorded", 2).containsEntry("rejected", 1);
    assertThat(
            accounts
                .jdbc()
                .queryForObject(
                    "SELECT LENGTH(route) FROM audit_log WHERE id=?",
                    Integer.class,
                    unmatchedPath.eventId()))
        .isEqualTo(300);
    assertThat(
            accounts
                .jdbc()
                .queryForObject(
                    "SELECT COUNT(*) FROM audit_log WHERE id IN (?,?)",
                    Integer.class,
                    valid.eventId(),
                    invalid.eventId()))
        .isEqualTo(1);
  }

  @Test
  void logSearchRequiresAdminAndPaginates() {
    var owner = owner();
    activities.record(
        new ActivityEvent(
            owner.workspaceId(),
            owner.id(),
            "Mario Audit",
            "m@test.it",
            "Test",
            "Errore test",
            "ERROR",
            "gas",
            "request",
            "POST",
            "/api/confronti",
            500));
    assertThatThrownBy(() -> activities.search("", "", "", "", "", 0, 30))
        .isInstanceOf(HttpProblem.class);
    RequestContext.set(
        new Identity("admin", "admin", "ADMIN", "Admin", "a@test.it", "ACTIVE", null, null, null));
    var results = activities.search("Mario Audit", "ERROR", "gas", "", "", 0, 1);
    assertThat(results.get("total")).isEqualTo(1L);
    assertThat((List<?>) results.get("items")).hasSize(1);
    assertThat(
            (List<?>) activities.search("Mario Audit", "ERROR", "gas", "", "", 1, 1).get("items"))
        .isEmpty();
  }
}
