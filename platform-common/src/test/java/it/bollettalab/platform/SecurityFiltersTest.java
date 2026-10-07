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
