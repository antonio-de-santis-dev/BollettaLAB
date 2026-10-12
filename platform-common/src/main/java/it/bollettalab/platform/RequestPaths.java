package it.bollettalab.platform;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import org.springframework.web.util.UriUtils;

/**
 * Canonical path used by the security filters. It matches the path Spring MVC routes on, so
 * variants such as "/api/confronti;x" or "/api/%63onfronti" cannot skip a check that compares
 * strings.
 */
public final class RequestPaths {
  private RequestPaths() {}

  public static String of(HttpServletRequest req) {
    String raw = req.getRequestURI();
    String decoded;
    try {
      decoded = UriUtils.decode(raw, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      throw new HttpProblem(400, "Indirizzo non valido");
    }
    if (raw.indexOf(';') >= 0
        || decoded.indexOf('%') >= 0
        || decoded.indexOf('\\') >= 0
        || decoded.contains("//")
        || decoded.contains("/./")
        || decoded.contains("/../")
        || decoded.endsWith("/.")
        || decoded.endsWith("/..")
        || decoded.chars().anyMatch(Character::isISOControl))
      throw new HttpProblem(400, "Indirizzo non valido");
    return decoded;
  }
}
