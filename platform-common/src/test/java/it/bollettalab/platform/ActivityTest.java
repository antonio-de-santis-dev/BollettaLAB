package it.bollettalab.platform;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.*;

class ActivityTest {
  @Test
  void pdfNamesUseSavedItalianDateAndSafeClientName() {
    assertThat(
            PdfNames.comparison(
                "Mario D’Èlia / SRL", "ProgettoGas", Instant.parse("2026-10-12T22:10:00Z")))
        .isEqualTo("Mario-D-Elia-SRL-ProgettoGas-2026-10-13.pdf");
    assertThat(PdfNames.comparison("../../", "ProgettoLuce", Instant.EPOCH))
        .isEqualTo("Cliente-ProgettoLuce-1970-01-01.pdf");
  }

  @Test
  void deniedRequestsAreAuditedWithoutCredentialsAndContextIsCleared() throws Exception {
    var events = new ArrayList<ActivityEvent>();
    var actor =
        new Identity(
            "id", "workspace", "PRIVATE", "Mario", "m@test.it", "ACTIVE", null, null, null);
    var filter =
        new PlatformFilter(
            r -> actor,
            new InternalClient("test"),
            new MockEnvironment().withProperty("platform.service", "utenti"),
            events::add);
    var request = new MockHttpServletRequest("GET", "/api/admin/users");
    request.setQueryString("password=SECRET&token=TOKEN");
    var response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(events).hasSize(1);
    assertThat(events.get(0).status()).isEqualTo("WARNING");
    assertThat(events.get(0).actorName()).isEqualTo("Mario");
    assertThat(events.get(0).toString()).doesNotContain("SECRET", "TOKEN");
    assertThat(RequestContext.identity()).isNull();
  }

  @Test
  void sinkFailureDoesNotChangeSuccessfulRequest() throws Exception {
    var actor =
        new Identity(
            "id", "workspace", "PRIVATE", "Mario", "m@test.it", "ACTIVE", null, null, null);
    var filter =
        new PlatformFilter(
            r -> actor,
            new InternalClient("test"),
            new MockEnvironment().withProperty("platform.service", "utenti"),
            e -> {
              throw new IllegalStateException("offline");
            });
    var response = new MockHttpServletResponse();
    filter.doFilter(
        new MockHttpServletRequest("GET", "/api/profile"), response, new MockFilterChain());
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(RequestContext.identity()).isNull();
  }
}
