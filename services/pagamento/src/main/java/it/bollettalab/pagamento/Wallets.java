package it.bollettalab.pagamento;

import it.bollettalab.platform.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Wallets {
  final JdbcTemplate db;
  final Environment env;
  final InternalClient client;

  Wallets(JdbcTemplate d, Environment e, InternalClient c) {
    db = d;
    env = e;
    client = c;
  }

  public JdbcTemplate jdbc() {
    return db;
  }

  public InternalClient internal() {
    return client;
  }

  static String id() {
    return UUID.randomUUID().toString();
  }

  static String s(Map<?, ?> m, String k) {
    return Objects.toString(m.get(k), "");
  }

  boolean dev() {
    return env.matchesProfiles("dev", "test");
  }

  void ensure(String w) {
    try {
      db.update("INSERT INTO wallets(workspace_id) VALUES(?)", w);
    } catch (DuplicateKeyException ignored) {
    }
  }

  Map<String, Object> lock(String w) {
    ensure(w);
    return db.queryForMap("SELECT * FROM wallets WHERE workspace_id=? FOR UPDATE", w);
  }

  boolean active(Map<String, Object> w) {
    return Boolean.TRUE.equals(w.get("active"))
        && w.get("period_end") != null
        && ((Timestamp) w.get("period_end")).toInstant().isAfter(Instant.now());
  }

  Map<String, Object> view(Map<String, Object> w) {
    boolean active = active(w);
    return Map.of(
        "workspaceId",
        s(w, "workspace_id"),
        "active",
        active,
        "monthlyRemaining",
        active ? ((Number) w.get("monthly_remaining")).intValue() : 0,
        "extraRemaining",
        ((Number) w.get("extra_remaining")).intValue(),
        "remaining",
        active
            ? ((Number) w.get("monthly_remaining")).intValue()
                + ((Number) w.get("extra_remaining")).intValue()
            : 0,
        "agentSeats",
        ((Number) w.get("agent_seats")).intValue(),
        "periodEnd",
        w.get("period_end") == null ? "" : w.get("period_end").toString(),
        "development",
        dev());
  }

  @Transactional
  public Map<String, Object> wallet(String w) {
    return view(lock(w));
  }

  void ledger(String w, String actor, int delta, String reason) {
    db.update(
        "INSERT INTO credit_ledger(id,workspace_id,actor_id,delta,reason) VALUES(?,?,?,?,?)",
        id(),
        w,
        actor,
        delta,
        reason);
  }

  @Transactional
  public Map<String, Object> checkout(Identity i, Map<String, Object> b) {
    if (i.agent() || i.admin())
      throw new HttpProblem(403, "Solo il titolare o il privato possono gestire il piano");
    if (!dev())
      throw new HttpProblem(
          503, "Stripe deve essere configurato prima di attivare i pagamenti reali");
    String product = s(b, "product"), key = s(b, "requestKey");
    if (!Set.of("BASE", "EXTRA_40", "EXTRA_100", "AGENT_1").contains(product)
        || key.length() < 10
        || key.length() > 100) throw new HttpProblem(400, "Prodotto o identificativo non valido");
    String w = i.workspaceId();
    var wallet = lock(w);
    var previous =
        db.queryForList(
            "SELECT product FROM purchases WHERE workspace_id=? AND request_key=?", w, key);
    if (!previous.isEmpty()) {
      if (!product.equals(s(previous.get(0), "product")))
        throw new HttpProblem(409, "Identificativo già usato");
      return view(wallet);
    }
    if (product.equals("BASE")) {
      if (active(wallet)) throw new HttpProblem(409, "Il piano è già attivo per questo mese");
      client.post(
          env.getProperty("platform.users-url") + "/internal/workspaces/" + w + "/activate",
          Map.of(),
          Map.class);
      Instant now = Instant.now();
      Instant end = ZonedDateTime.ofInstant(now, ZoneOffset.UTC).plusMonths(1).toInstant();
      db.update(
          "UPDATE wallets SET monthly_remaining=80,period_start=?,period_end=?,active=TRUE WHERE"
              + " workspace_id=?",
          Timestamp.from(now),
          Timestamp.from(end),
          w);
      ledger(w, i.id(), 80, "Attivazione mensile BASE — prova senza addebito");
    } else {
      if (!active(wallet)) throw new HttpProblem(409, "Attiva prima il piano base");
      if (product.equals("AGENT_1")) {
        if (!i.owner()) throw new HttpProblem(403, "Posti agente disponibili solo per le imprese");
        db.update("UPDATE wallets SET agent_seats=agent_seats+1 WHERE workspace_id=?", w);
      } else {
        int credits = product.equals("EXTRA_40") ? 40 : 100;
        db.update(
            "UPDATE wallets SET extra_remaining=extra_remaining+? WHERE workspace_id=?",
            credits,
            w);
        ledger(w, i.id(), credits, "Pacchetto " + product + " — prova senza addebito");
      }
    }
    db.update(
        "INSERT INTO purchases(id,workspace_id,request_key,product) VALUES(?,?,?,?)",
        id(),
        w,
        key,
        product);
    return view(db.queryForMap("SELECT * FROM wallets WHERE workspace_id=?", w));
  }

  @Transactional
  public Map<String, Object> reserve(Map<String, Object> b) {
    String w = s(b, "workspaceId"),
        key = s(b, "requestKey"),
        sim = s(b, "simulator"),
        hash = s(b, "hash");
    if (key.length() < 10
        || key.length() > 100
        || !Set.of("luce", "luce-business", "gas").contains(sim)
        || hash.length() != 64) throw new HttpProblem(400, "Richiesta non valida");
    var wallet = lock(w);
    var old =
        db.queryForList(
            "SELECT * FROM reservations WHERE workspace_id=? AND simulator=? AND request_key=?",
            w,
            sim,
            key);
    if (!old.isEmpty()) {
      var r = old.get(0);
      if (!hash.equals(s(r, "request_hash")) || !s(b, "userId").equals(s(r, "user_id")))
        throw new HttpProblem(409, "Identificativo già usato per una richiesta diversa");
      if ("COMPLETE".equals(s(r, "state")))
        return Map.of("id", s(r, "id"), "completed", true, "response", s(r, "response_json"));
      if ("RESERVED".equals(s(r, "state")))
        throw new HttpProblem(409, "Simulazione in elaborazione. Riprova tra poco");
      db.update("DELETE FROM reservations WHERE id=?", s(r, "id"));
    }
    if (!active(wallet)) throw new HttpProblem(402, "Attiva o rinnova il piano per simulare");
    int monthly = ((Number) wallet.get("monthly_remaining")).intValue(),
        extra = ((Number) wallet.get("extra_remaining")).intValue();
    if (monthly + extra <= 0)
      throw new HttpProblem(402, "Simulazioni esaurite. Modifica il piano o acquista un pacchetto");
    String bucket = monthly > 0 ? "MONTHLY" : "EXTRA", rid = id();
    db.update(
        "UPDATE wallets SET "
            + (monthly > 0 ? "monthly_remaining" : "extra_remaining")
            + "="
            + (monthly > 0 ? "monthly_remaining" : "extra_remaining")
            + "-1 WHERE workspace_id=?",
        w);
    db.update(
        "INSERT INTO"
            + " reservations(id,workspace_id,user_id,author_name,company_name,simulator,operation_kind,request_key,request_hash,state,bucket)"
            + " VALUES(?,?,?,?,?,?,?,?,?,'RESERVED',?)",
        rid,
        w,
        s(b, "userId"),
        s(b, "authorName"),
        s(b, "companyName"),
        sim,
        "business".equals(s(b, "kind")) ? "business" : "comparison",
        key,
        hash,
        bucket);
    return Map.of("id", rid, "completed", false);
  }

  @Transactional
  public void complete(String id, Map<String, Object> b) {
    var first = db.queryForMap("SELECT * FROM reservations WHERE id=?", id);
    lock(s(first, "workspace_id"));
    var r = db.queryForMap("SELECT * FROM reservations WHERE id=? FOR UPDATE", id);
    if ("COMPLETE".equals(s(r, "state"))) return;
    if (!"RESERVED".equals(s(r, "state"))) throw new HttpProblem(409, "Operazione annullata");
    db.update(
        "UPDATE reservations SET state='COMPLETE',result_id=?,response_json=? WHERE id=?",
        b.get("resultId"),
        s(b, "response"),
        id);
    ledger(s(r, "workspace_id"), s(r, "user_id"), -1, "Simulazione " + s(r, "simulator"));
  }

  @Transactional
  public void release(String id) {
    var first = db.queryForMap("SELECT * FROM reservations WHERE id=?", id);
    lock(s(first, "workspace_id"));
    var r = db.queryForMap("SELECT * FROM reservations WHERE id=? FOR UPDATE", id);
    if (!"RESERVED".equals(s(r, "state"))) return;
    var w = lock(s(r, "workspace_id"));
    boolean samePeriod =
        ((Timestamp) r.get("created_at")).compareTo((Timestamp) w.get("period_start")) >= 0;
    String bucket = "EXTRA".equals(s(r, "bucket")) ? "extra_remaining" : "monthly_remaining";
    if (bucket.equals("extra_remaining") || samePeriod)
      db.update(
          "UPDATE wallets SET " + bucket + "=" + bucket + "+1 WHERE workspace_id=?",
          s(r, "workspace_id"));
    db.update("UPDATE reservations SET state='FAILED' WHERE id=?", id);
  }

  @Transactional
  public Map<String, Object> adjust(String w, Map<String, Object> body) {
    RequestContext.admin();
    var wallet = lock(w);
    Object amount = body.get("delta");
    if (!(amount instanceof Number)
        || ((Number) amount).doubleValue() != ((Number) amount).intValue())
      throw new HttpProblem(400, "Inserisci un numero intero");
    int delta = ((Number) amount).intValue();
    String reason = s(body, "reason");
    if (Math.abs((long) delta) > 100000 || reason.isBlank() || reason.length() > 180)
      throw new HttpProblem(400, "Quantità o motivazione non valida");
    int total =
        ((Number) wallet.get("monthly_remaining")).intValue()
            + ((Number) wallet.get("extra_remaining")).intValue();
    if (delta < 0 && total + delta < 0)
      throw new HttpProblem(409, "Non puoi ridurre le simulazioni sotto zero");
    if (delta >= 0)
      db.update(
          "UPDATE wallets SET extra_remaining=extra_remaining+? WHERE workspace_id=?", delta, w);
    else {
      int extra = ((Number) wallet.get("extra_remaining")).intValue(),
          take = Math.min(extra, -delta);
      db.update(
          "UPDATE wallets SET"
              + " extra_remaining=extra_remaining-?,monthly_remaining=monthly_remaining-? WHERE"
              + " workspace_id=?",
          take,
          -delta - take,
          w);
    }
    ledger(w, RequestContext.required().id(), delta, "ADMIN: " + reason);
    return view(db.queryForMap("SELECT * FROM wallets WHERE workspace_id=?", w));
  }
}
