package it.bollettalab.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.web.filter.OncePerRequestFilter;

public class PlatformFilter extends OncePerRequestFilter {
  private final ActivitySink activities;
  private final IdentityProvider identities;
  private final InternalClient client;
  private final Environment env;
  private final ObjectMapper json = new ObjectMapper();
  private final Set<String> publicPaths =
      Set.of(
          "/api/auth/register",
          "/api/auth/login",
          "/api/auth/forgot-password",
          "/api/auth/reset-password",
          "/api/auth/verify-email",
          "/api/auth/accept-invite",
          "/api/auth/config");

  public PlatformFilter(IdentityProvider identities, InternalClient client, Environment env) {
    this(identities, client, env, event -> {});
  }

  public PlatformFilter(
      IdentityProvider identities,
      InternalClient client,
      Environment env,
      ActivitySink activities) {
    this.activities = activities;
    this.identities = identities;
    this.client = client;
    this.env = env;
    if (!env.getProperty("platform.security.enabled", Boolean.class, true)
        && !env.matchesProfiles("test"))
      throw new IllegalStateException(
          "La disattivazione delle protezioni è consentita solo nei test");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    res.setHeader("X-Content-Type-Options", "nosniff");
    res.setHeader("Cache-Control", "no-store");
    res.setHeader("X-Request-Id", UUID.randomUUID().toString());
    boolean failed = false;
    try {
      String path = RequestPaths.of(req);
      if (!env.getProperty("platform.security.enabled", Boolean.class, true)
          || path.equals("/actuator/health")
          || path.startsWith("/actuator/health/")) {
        chain.doFilter(req, res);
        return;
      }
      if (path.startsWith("/internal/")) {
        if (!Tokens.same(req.getHeader("X-Internal-Key"), env.getProperty("platform.internal-key")))
          throw new HttpProblem(403, "Accesso interno non autorizzato");
        chain.doFilter(req, res);
        return;
      }
      if (!path.startsWith("/api/")) throw new HttpProblem(404, "Risorsa non trovata");
      if (!Set.of("GET", "HEAD", "OPTIONS").contains(req.getMethod())
          && !"BollettaLAB".equals(req.getHeader("X-Requested-With")))
        throw new HttpProblem(403, "Richiesta non autorizzata");
      if (publicPaths.contains(path)) {
        chain.doFilter(req, res);
        return;
      }
      Identity identity = identities.authenticate(req);
      RequestContext.set(identity);
      if (path.startsWith("/api/admin/")) RequestContext.admin();
      boolean simulator =
          !Set.of("utenti", "pagamento").contains(env.getProperty("platform.service"));
      if (simulator && !identity.admin()) {
        if (!"ACTIVE".equals(identity.status()))
          throw new HttpProblem(403, "Completa l’attivazione dell’account");
        if (!req.getMethod().equals("GET")) {
          var access =
              client.get(
                  env.getProperty("platform.payment-url", "http://pagamento:8080")
                      + "/internal/access/"
                      + identity.workspaceId(),
                  Map.class);
          if (!Boolean.TRUE.equals(access.get("active")))
            throw new HttpProblem(402, "Attiva o rinnova il piano per continuare");
        }
      }
      if (simulator
          && !"gas".equals(env.getProperty("platform.service"))
          && (path.equals("/api/fonti") || path.startsWith("/api/fonti/"))
          && !req.getMethod().equals("GET")
          && !identity.admin())
        throw new HttpProblem(403, "Le fonti ufficiali globali sono gestite dall’amministratore");
      chain.doFilter(req, res);
    } catch (HttpProblem e) {
      if (!res.isCommitted()) {
        res.resetBuffer();
        res.setStatus(e.status());
        res.setContentType("application/json");
        json.writeValue(res.getOutputStream(), Map.of("message", e.getMessage()));
      }
    } catch (org.springframework.web.client.RestClientException e) {
      if (!res.isCommitted()) {
        res.resetBuffer();
        res.setStatus(503);
        res.setContentType("application/json");
        json.writeValue(
            res.getOutputStream(),
            Map.of("message", "Servizio temporaneamente non disponibile. Riprova."));
      }
    } catch (ServletException | IOException | RuntimeException e) {
      failed = true;
      throw e;
    } finally {
      try {
        recordActivity(req, res, failed);
      } catch (RuntimeException e) {
        org.slf4j.LoggerFactory.getLogger(getClass())
            .warn("Activity recording failed: requestId={}", res.getHeader("X-Request-Id"));
      }
      RequestContext.clear();
    }
  }

  private void recordActivity(HttpServletRequest req, HttpServletResponse res, boolean failed) {
    String path;
    try {
      path = RequestPaths.of(req);
    } catch (HttpProblem e) {
      path = "/api/percorso-non-valido";
    }
    if (!path.startsWith("/api/")
        || !env.getProperty("platform.security.enabled", Boolean.class, true)) return;
    int code = failed ? 500 : res.getStatus();
    if (code < 400
        && Set.of(
                "/api/auth/me",
                "/api/auth/config",
                "/api/wallet",
                "/api/admin/logs",
                "/api/activity")
            .contains(path)) return;
    Identity actor = RequestContext.identity();
    if (actor == null && req.getAttribute("lab.audit.identity") instanceof Identity login)
      actor = login;
    String method = req.getMethod(), service = env.getProperty("platform.service", "unknown");
    Object matched =
        req.getAttribute(
            org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    String route = matched == null ? path : matched.toString();
    String action = ActivityLabels.action(path, method);
    String state = code >= 500 ? "ERROR" : code >= 400 ? "WARNING" : "COMPLETED";
    activities.record(
        new ActivityEvent(
            actor == null ? null : actor.workspaceId(),
            actor == null ? null : actor.id(),
            actor == null ? "Accesso non autenticato" : actor.name(),
            actor == null ? "" : actor.email(),
            action,
            action + " · " + service + " · HTTP " + code,
            state,
            service,
            res.getHeader("X-Request-Id"),
            method,
            route,
            code));
  }
}
