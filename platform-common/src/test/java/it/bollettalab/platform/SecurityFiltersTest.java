package it.bollettalab.platform;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.*;

class SecurityFiltersTest {
  Identity privateUser() {
    return new Identity(
        "user", "workspace", "PRIVATE", "Utente", "u@example.test", "ACTIVE", null, null, null);
  }

  @AfterEach
  void clear() {
    RequestContext.clear();
  }

  @Test
  void loginPublicRequiresCsrfHeaderAndInternalApisRequireKey() throws Exception {
    var env =
        new MockEnvironment()
            .withProperty("platform.service", "utenti")
            .withProperty("platform.internal-key", "test-key");
    var filter =
        new PlatformFilter(
            r -> {
              throw new HttpProblem(401, "Accedi");
            },
            new InternalClient("test-key"),
            env);
    var request = new MockHttpServletRequest("POST", "/api/auth/login");
    var response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    assertThat(response.getStatus()).isEqualTo(403);
    request = new MockHttpServletRequest("POST", "/api/auth/login");
    request.addHeader("X-Requested-With", "BollettaLAB");
    response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    assertThat(response.getStatus()).isEqualTo(200);
    request = new MockHttpServletRequest("GET", "/internal/identity");
    response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    assertThat(response.getStatus()).isEqualTo(403);
  }

  @Test
  void usersCannotEnterAdminAndThreadContextIsCleared() throws Exception {
    var env = new MockEnvironment().withProperty("platform.service", "utenti");
    var filter = new PlatformFilter(r -> privateUser(), new InternalClient("test"), env);
    var response = new MockHttpServletResponse();
    filter.doFilter(
        new MockHttpServletRequest("GET", "/api/admin/users"), response, new MockFilterChain());
    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(RequestContext.identity()).isNull();
  }

  @Test
  void pathVariantsCannotSkipAdminSourcesOrCreditChecks() throws Exception {
    var env = new MockEnvironment().withProperty("platform.service", "luce");
    var client = mock(InternalClient.class);
    when(client.get(anyString(), eq(Map.class))).thenReturn(Map.of("active", true));
    var filter = new PlatformFilter(r -> privateUser(), client, env);
    for (String path :
        List.of(
            "/api/confronti;x",
            "/api/fonti;x/dati",
            "/api/%2561dmin/users",
            "/api//admin/users",
            "/api/x/../admin/users")) {
      var request = new MockHttpServletRequest("PUT", path);
      request.addHeader("X-Requested-With", "BollettaLAB");
      var chain = new MockFilterChain();
      var response = new MockHttpServletResponse();
      filter.doFilter(request, response, chain);
      assertThat(response.getStatus()).as(path).isEqualTo(400);
      assertThat(chain.getRequest()).as(path).isNull();
    }
    for (String path : List.of("/api/fonti/dati", "/api/%66onti/dati", "/api/%61dmin/users")) {
      var request = new MockHttpServletRequest("PUT", path);
      request.addHeader("X-Requested-With", "BollettaLAB");
      var chain = new MockFilterChain();
      var response = new MockHttpServletResponse();
      filter.doFilter(request, response, chain);
      assertThat(response.getStatus()).as(path).isEqualTo(403);
      assertThat(chain.getRequest()).as(path).isNull();
    }
    RequestContext.set(privateUser());
    var credits =
        new CreditFilter(client, mock(LocalReceipts.class), env, new ObjectMapper());
    var encoded = new MockHttpServletRequest("POST", "/api/%63onfronti");
    assertThat(credits.shouldNotFilter(encoded)).isFalse();
  }

  @Test
  void cachedOperationKeepsUtf8AndCreationStatus() throws Exception {
    RequestContext.set(privateUser());
    var client = mock(InternalClient.class);
    String payload = "{\"id\":1,\"name\":\"Impresa · è\"}";
    when(client.post(anyString(), any(), eq(Map.class)))
        .thenReturn(Map.of("id", "operation", "completed", true, "response", payload));
    var filter =
        new CreditFilter(
            client,
            mock(LocalReceipts.class),
            new MockEnvironment().withProperty("platform.service", "gas"),
            new ObjectMapper());
    var request = new MockHttpServletRequest("POST", "/api/confronti");
    request.setContent("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    request.addHeader("X-Idempotency-Key", UUID.randomUUID().toString());
    var response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    assertThat(response.getStatus()).isEqualTo(201);
    assertThat(
            new String(response.getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8))
        .isEqualTo(payload);
  }
}
