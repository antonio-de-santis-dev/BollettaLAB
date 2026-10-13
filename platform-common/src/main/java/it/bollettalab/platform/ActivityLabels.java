package it.bollettalab.platform;

import java.util.List;
import java.util.Map;

/** Ordered specific routes first; labels describe behavior, never request values. */
final class ActivityLabels {
  private ActivityLabels() {}

  private static final List<Map.Entry<String, String>> SUBJECTS =
      List.of(
          Map.entry("/reset-password", "reimpostazione password"),
          Map.entry("/auth/login", "accesso account"),
          Map.entry("/auth/logout", "uscita account"),
          Map.entry("/auth/register", "registrazione account"),
          Map.entry("/profile", "profilo account"),
          Map.entry("/agents", "gestione agenti"),
          Map.entry("/confronti", "confronto"),
          Map.entry("/simulazioni", "simulazione business"),
          Map.entry("/offerte", "offerta"),
          Map.entry("/bollette", "bolletta"),
          Map.entry("/parametri", "parametri gestore"),
          Map.entry("/checkout", "acquisto piano"),
          Map.entry("/wallets", "quote account"),
          Map.entry("/fonti", "fonti ufficiali"),
          Map.entry("/admin", "amministrazione"));

  static String action(String path, String method) {
    String subject =
        SUBJECTS.stream()
            .filter(e -> path.contains(e.getKey()))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse("operazione account");
    String verb =
        switch (method) {
          case "POST" -> "Creazione / esecuzione";
          case "PUT", "PATCH" -> "Aggiornamento";
          case "DELETE" -> "Eliminazione";
          default -> "Consultazione";
        };
    if (path.endsWith("/pdf")) verb = "Download PDF";
    if (path.endsWith("/duplica")) verb = "Duplicazione";
    return verb + " · " + subject;
  }
}
