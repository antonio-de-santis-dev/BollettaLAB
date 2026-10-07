package it.bollettalab.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

public class CreditFilter extends OncePerRequestFilter {
  static final java.util.Set<String> running = java.util.concurrent.ConcurrentHashMap.newKeySet();
  final InternalClient client;
  final LocalReceipts receipts;
  final Environment env;
  final ObjectMapper json;

  public CreditFilter(InternalClient c, LocalReceipts r, Environment e, ObjectMapper j) {
    client = c;
    receipts = r;
    env = e;
    json = j;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest r) {
    return !r.getMethod().equals("POST")
        || !Set.of("/api/confronti", "/api/business/simulazioni").contains(r.getRequestURI())
        || RequestContext.identity() == null
        || RequestContext.required().admin();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws IOException, ServletException {
    res.setCharacterEncoding("UTF-8");
    Identity i = RequestContext.required();
    String url = env.getProperty("platform.payment-url", "http://pagamento:8080"),
        service = env.getProperty("platform.service");
    String id = null;
    try {
      byte[] bytes = req.getInputStream().readNBytes(2_000_001);
      if (bytes.length > 2_000_000) throw new HttpProblem(413, "Richiesta troppo grande");
      String key = Objects.toString(req.getHeader("X-Idempotency-Key"), "");
      var reservation =
          client.post(
              url + "/internal/reservations",
              Map.of(
                  "workspaceId",
                  i.workspaceId(),
                  "userId",
                  i.id(),
                  "authorName",
                  i.name(),
                  "companyName",
                  Objects.toString(i.companyName(), ""),
                  "simulator",
                  service,
                  "kind",
                  req.getRequestURI().equals("/api/business/simulazioni")
                      ? "business"
                      : "comparison",
                  "requestKey",
                  key,
                  "hash",
                  Tokens.hash(
                      req.getRequestURI()
                          + "\n"
                          + new String(bytes, java.nio.charset.StandardCharsets.UTF_8))),
              Map.class);
      if (Boolean.TRUE.equals(reservation.get("completed"))) {
        res.setStatus(201);
        res.setContentType("application/json");
        res.getWriter().write(reservation.get("response").toString());
        return;
      }
      id = reservation.get("id").toString();
      running.add(id);
      LocalReceipts.operation.set(id);
      var cached = new ContentCachingResponseWrapper(res);
      final byte[] body = bytes;
      HttpServletRequest wrapped =
          new HttpServletRequestWrapper(req) {
            public ServletInputStream getInputStream() {
              var input = new ByteArrayInputStream(body);
              return new ServletInputStream() {
                public int read() {
                  return input.read();
                }

                public boolean isFinished() {
                  return input.available() == 0;
                }

                public boolean isReady() {
                  return true;
                }

                public void setReadListener(ReadListener listener) {}
              };
            }

            public BufferedReader getReader() {
              return new BufferedReader(
                  new InputStreamReader(getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            }
          };
      chain.doFilter(wrapped, cached);
      String response =
          new String(cached.getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8);
      if (cached.getStatus() >= 200 && cached.getStatus() < 300) {
        long result = json.readTree(response).get("id").asLong();
        try {
          receipts.complete(id, result, response);
        } catch (RuntimeException ignored) {
          /* persisted receipt is retried; the simulation remains charged exactly once */
        }
      } else client.post(url + "/internal/reservations/" + id + "/release", Map.of(), Map.class);
      cached.copyBodyToResponse();
    } catch (HttpProblem e) {
      res.setStatus(e.status());
      res.setContentType("application/json");
      json.writeValue(res.getWriter(), Map.of("message", e.getMessage()));
    } finally {
      if (id != null) running.remove(id);
      LocalReceipts.operation.remove();
    }
  }
}
