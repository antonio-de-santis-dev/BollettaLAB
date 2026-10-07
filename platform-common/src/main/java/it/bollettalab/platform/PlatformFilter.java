package it.bollettalab.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.web.filter.OncePerRequestFilter;

public class PlatformFilter extends OncePerRequestFilter {
  private final IdentityProvider identities;
  private final InternalClient client;
  private final Environment env;
  private final ObjectMapper json = new ObjectMapper();
  private final Set<String> publicPaths = Set.of("/api/auth/register", "/api/auth/login", "/api/auth/forgot", "/api/auth/reset", "/api/auth/verify", "/api/auth/accept-invite", "/api/auth/config");
  public PlatformFilter(IdentityProvider identities, InternalClient client, Environment env) {
    this.identities = identities; this.client = client; this.env = env;
    if (!env.getProperty("platform.security.enabled", Boolean.class, true) && !env.matchesProfiles("test"))
      throw new IllegalStateException("La disattivazione delle protezioni è consentita solo nei test");
  }
  @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
    res.setHeader("X-Content-Type-Options", "nosniff");
    res.setHeader("Cache-Control", "no-store");
    res.setHeader("X-Request-Id", UUID.randomUUID().toString());
    String path = req.getRequestURI();
    try {
      if (!env.getProperty("platform.security.enabled", Boolean.class, true) || path.equals("/actuator/health") || path.startsWith("/actuator/health/")) { chain.doFilter(req, res); return; }
      if (path.startsWith("/internal/")) {
        if (!Tokens.same(req.getHeader("X-Internal-Key"), env.getProperty("platform.internal-key"))) throw new HttpProblem(403, "Accesso interno non autorizzato");
        chain.doFilter(req, res); return;
      }
      if (!path.startsWith("/api/")) throw new HttpProblem(404, "Risorsa non trovata");
      if (!Set.of("GET", "HEAD", "OPTIONS").contains(req.getMethod()) && !"BollettaLAB".equals(req.getHeader("X-Requested-With")))
        throw new HttpProblem(403, "Richiesta non autorizzata");
      if (publicPaths.contains(path)) { chain.doFilter(req, res); return; }
      Identity identity = identities.authenticate(req); RequestContext.set(identity);
      if (path.startsWith("/api/admin/")) RequestContext.admin();
      boolean simulator = !Set.of("utenti", "pagamento").contains(env.getProperty("platform.service"));
      if (simulator && !identity.admin()) {
        if (!identity.status().equals("ACTIVE")) throw new HttpProblem(403, "Completa l’attivazione dell’account");
        if (!req.getMethod().equals("GET")) client.get(env.getProperty("platform.payments-url", "http://pagamento:8080") + "/internal/access/" + identity.workspaceId(), Map.class);
      }
      if (simulator && path.startsWith("/api/fonti/") && !req.getMethod().equals("GET") && !identity.admin())
        throw new HttpProblem(403, "Le fonti ufficiali globali sono gestite dall’amministratore");
      chain.doFilter(req, res);
    } catch (HttpProblem e) {
      if (!res.isCommitted()) { res.resetBuffer(); res.setStatus(e.status()); res.setContentType("application/json"); json.writeValue(res.getOutputStream(), Map.of("message", e.getMessage())); }
    } catch (org.springframework.web.client.RestClientException e) {
      if (!res.isCommitted()) { res.resetBuffer(); res.setStatus(503); res.setContentType("application/json"); json.writeValue(res.getOutputStream(), Map.of("message", "Servizio temporaneamente non disponibile. Riprova.")); }
    } finally { RequestContext.clear(); }
  }
}
