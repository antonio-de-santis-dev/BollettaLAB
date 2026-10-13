package it.bollettalab.platform;

import java.text.Normalizer;
import java.time.Instant;
import java.time.ZoneId;

/** File names come from the immutable comparison, including its original creation date. */
public final class PdfNames {
  private PdfNames() {}

  public static String comparison(String client, String simulator, Instant created) {
    String safe =
        Normalizer.normalize(client == null ? "Cliente" : client, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replaceAll("[^a-zA-Z0-9]+", "-")
            .replaceAll("^-|-$", "");
    if (safe.isBlank()) safe = "Cliente";
    if (safe.length() > 100) safe = safe.substring(0, 100).replaceAll("-$", "");
    return safe
        + "-"
        + simulator
        + "-"
        + created.atZone(ZoneId.of("Europe/Rome")).toLocalDate()
        + ".pdf";
  }
}
