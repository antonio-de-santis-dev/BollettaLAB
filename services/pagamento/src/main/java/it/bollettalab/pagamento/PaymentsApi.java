package it.bollettalab.pagamento;

import it.bollettalab.platform.*;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
public class PaymentsApi {
  final Wallets wallets;

  PaymentsApi(Wallets w) {
    wallets = w;
  }

  @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 60000)
  public void reconcile() {
    for (var r :
        wallets
            .jdbc()
            .queryForList(
                "SELECT id,simulator FROM reservations WHERE state='RESERVED' AND created_at<?",
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(900)))) {
      try {
        var status =
            wallets
                .internal()
                .get(
                    "http://" + r.get("simulator") + ":8080/internal/operations/" + r.get("id"),
                    Map.class);
        if (Boolean.TRUE.equals(status.get("saved")))
          wallets.complete(
              r.get("id").toString(),
              Map.of("resultId", status.get("resultId"), "response", status.get("response")));
        else if (Boolean.FALSE.equals(status.get("running")))
          wallets.release(r.get("id").toString());
      } catch (RuntimeException ignored) {
      }
    }
  }

  @GetMapping("/api/wallet")
  Object wallet() {
    return wallets.wallet(RequestContext.required().workspaceId());
  }

  @GetMapping("/api/catalog")
  Object catalog() {
    return Map.of(
        "development",
        wallets.dev(),
        "provider",
        "Stripe (configurazione finale)",
        "products",
        List.of(
            Map.of(
                "id",
                "BASE",
                "name",
                "Piano base mensile",
                "description",
                "80 simulazioni al mese · 2 agenti per l'impresa",
                "price",
                "Da definire"),
            Map.of(
                "id",
                "EXTRA_40",
                "name",
                "40 simulazioni aggiuntive",
                "description",
                "Credito condiviso tra i tre simulatori",
                "price",
                "Da definire"),
            Map.of(
                "id",
                "EXTRA_100",
                "name",
                "100 simulazioni aggiuntive",
                "description",
                "Credito condiviso tra i tre simulatori",
                "price",
                "Da definire"),
            Map.of(
                "id",
                "AGENT_1",
                "name",
                "Un agente aggiuntivo",
                "description",
                "Un nuovo posto nell'impresa",
                "price",
                "Da definire")));
  }

  @PostMapping("/api/checkout")
  Object checkout(@RequestBody Map<String, Object> b) {
    return wallets.checkout(RequestContext.required(), b);
  }

  @GetMapping("/api/history")
  Object history() {
    Identity i = RequestContext.required();
    String sql =
        "SELECT id,user_id,author_name,company_name,simulator,operation_kind,result_id,created_at"
            + " FROM reservations WHERE workspace_id=? AND state='COMPLETE'";
    return i.agent()
        ? wallets
            .jdbc()
            .queryForList(sql + " AND user_id=? ORDER BY created_at DESC", i.workspaceId(), i.id())
        : wallets.jdbc().queryForList(sql + " ORDER BY created_at DESC", i.workspaceId());
  }

  @GetMapping("/api/company/usage")
  Object usage() {
    RequestContext.owner();
    return wallets
        .jdbc()
        .queryForList(
            "SELECT user_id,author_name,COUNT(*) simulations FROM reservations WHERE workspace_id=?"
                + " AND state='COMPLETE' GROUP BY user_id,author_name",
            RequestContext.required().workspaceId());
  }

  @GetMapping("/api/admin/wallets")
  Object all() {
    RequestContext.admin();
    return wallets.jdbc().queryForList("SELECT * FROM wallets");
  }

  @GetMapping("/api/admin/ledger")
  Object allLedger() {
    RequestContext.admin();
    return wallets
        .jdbc()
        .queryForList("SELECT * FROM credit_ledger ORDER BY created_at DESC LIMIT 300");
  }

  @GetMapping("/api/admin/ledger/{workspace}")
  Object ledger(@PathVariable String workspace) {
    RequestContext.admin();
    return wallets
        .jdbc()
        .queryForList(
            "SELECT * FROM credit_ledger WHERE workspace_id=? ORDER BY created_at DESC", workspace);
  }

  @PostMapping("/api/admin/wallets/{workspace}")
  Object adjust(@PathVariable String workspace, @RequestBody Map<String, Object> b) {
    return wallets.adjust(workspace, b);
  }

  @GetMapping("/internal/access/{workspace}")
  Object access(@PathVariable String workspace) {
    return wallets.wallet(workspace);
  }

  @PostMapping("/internal/reservations")
  Object reserve(@RequestBody Map<String, Object> b) {
    return wallets.reserve(b);
  }

  @PostMapping("/internal/reservations/{id}/complete")
  Object complete(@PathVariable String id, @RequestBody Map<String, Object> b) {
    wallets.complete(id, b);
    return Map.of("completed", true);
  }

  @PostMapping("/internal/reservations/{id}/release")
  Object release(@PathVariable String id) {
    wallets.release(id);
    return Map.of("released", true);
  }

  @DeleteMapping("/internal/workspaces/{id}")
  @Transactional
  Object delete(@PathVariable String id) {
    for (String t : List.of("reservations", "purchases", "credit_ledger", "wallets"))
      wallets.jdbc().update("DELETE FROM " + t + " WHERE workspace_id=?", id);
    return Map.of("deleted", true);
  }
}
