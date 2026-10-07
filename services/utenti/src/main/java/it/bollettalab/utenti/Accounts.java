package it.bollettalab.utenti;

import it.bollettalab.platform.*;
import jakarta.servlet.http.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Accounts {
  final JdbcTemplate db;
  final Environment env;
  final JavaMailSender mail;
  final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
  final ConcurrentHashMap<String, List<Instant>> attempts = new ConcurrentHashMap<>();

  Accounts(JdbcTemplate db, Environment env, JavaMailSender mail) {
    this.db = db;
    this.env = env;
    this.mail = mail;
  }

  boolean dev() {
    return env.matchesProfiles("dev", "test");
  }

  public JdbcTemplate jdbc() {
    return db;
  }

  static String id() {
    return UUID.randomUUID().toString();
  }

  static String str(Map<String, ?> m, String k) {
    Object v = m.get(k);
    return v == null ? "" : v.toString().trim();
  }

  static String email(String s) {
    s = s.trim().toLowerCase(Locale.ROOT);
    if (s.length() > 254 || !s.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"))
      throw new HttpProblem(400, "Email non valida");
    return s;
  }

  static String secret(Map<String, ?> m) {
    return Objects.toString(m.get("password"), "");
  }

  static void password(String s) {
    if (s.isBlank()
        || s.length() < 10
        || s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new HttpProblem(
          400, "La password deve avere almeno 10 caratteri e non superare 72 byte UTF-8");
  }

  void audit(String action, String subject) {
    Identity i = RequestContext.identity();
    db.update(
        "INSERT INTO audit_log(id,workspace_id,actor_id,action,subject_id) VALUES(?,?,?,?,?)",
        id(),
        i == null ? null : i.workspaceId(),
        i == null ? null : i.id(),
        action,
        subject);
  }

  Map<String, Object> account(String id) {
    return db.queryForMap(
        "SELECT a.*,w.status workspace_status,w.name company_name,w.vat_number,w.address,w.type"
            + " workspace_type FROM accounts a JOIN workspaces w ON w.id=a.workspace_id WHERE"
            + " a.id=?",
        id);
  }

  Identity identity(Map<String, Object> m) {
    return new Identity(
        str(m, "id"),
        str(m, "workspace_id"),
        str(m, "role"),
        str(m, "name"),
        str(m, "email"),
        !"ACTIVE".equals(str(m, "status")) ? str(m, "status") : str(m, "workspace_status"),
        "COMPANY".equals(str(m, "workspace_type")) ? str(m, "company_name") : null,
        str(m, "vat_number"),
        str(m, "address"));
  }

  @Bean
  @org.springframework.context.annotation.Primary
  IdentityProvider localIdentity() {
    return this::authenticate;
  }

  Identity authenticate(HttpServletRequest request) {
    String cookie = null;
    if (request.getCookies() != null)
      for (Cookie c : request.getCookies())
        if (c.getName().equals("BL_SESSION")) cookie = c.getValue();
    if (cookie == null) throw new HttpProblem(401, "Accedi per continuare");
    var rows =
        db.queryForList(
            "SELECT user_id FROM sessions WHERE digest=? AND expires_at>?",
            Tokens.hash(cookie),
            Timestamp.from(Instant.now()));
    if (rows.isEmpty()) throw new HttpProblem(401, "Sessione scaduta");
    var m = account(str(rows.get(0), "user_id"));
    if ("BLOCKED".equals(str(m, "status"))
        || Set.of("BLOCKED", "DELETING").contains(str(m, "workspace_status")))
      throw new HttpProblem(403, "Profilo bloccato");
    return identity(m);
  }

  void cookie(HttpServletResponse response, String token) {
    response.addHeader(
        "Set-Cookie",
        ResponseCookie.from("BL_SESSION", token)
            .httpOnly(true)
            .secure(env.getProperty("platform.cookie-secure", Boolean.class, true))
            .sameSite("Strict")
            .path("/")
            .maxAge(token.isEmpty() ? 0 : 43200)
            .build()
            .toString());
  }

  void throttle(String ip) {
    Instant now = Instant.now();
    attempts
        .entrySet()
        .removeIf(e -> e.getValue().stream().allMatch(t -> t.isBefore(now.minusSeconds(900))));
    List<Instant> times =
        attempts.computeIfAbsent(ip, k -> Collections.synchronizedList(new ArrayList<>()));
    synchronized (times) {
      times.removeIf(t -> t.isBefore(now.minusSeconds(900)));
      if (times.size() >= 15) throw new HttpProblem(429, "Troppi tentativi. Riprova tra 15 minuti");
      times.add(now);
    }
  }

  @Transactional
  public Identity login(
      Map<String, Object> body, HttpServletRequest request, HttpServletResponse response) {
    throttle(request.getRemoteAddr());
    String em = email(str(body, "email"));
    var rows = db.queryForList("SELECT id,password_hash,status FROM accounts WHERE email=?", em);
    if (rows.isEmpty() || !encoder.matches(secret(body), str(rows.get(0), "password_hash")))
      throw new HttpProblem(401, "Email o password errate");
    var m = account(str(rows.get(0), "id"));
    if ("BLOCKED".equals(str(m, "status"))
        || Set.of("BLOCKED", "DELETING").contains(str(m, "workspace_status")))
      throw new HttpProblem(403, "Profilo bloccato");
    String token = Tokens.create();
    db.update("DELETE FROM sessions WHERE expires_at<?", Timestamp.from(Instant.now()));
    db.update(
        "INSERT INTO sessions(digest,user_id,expires_at) VALUES(?,?,?)",
        Tokens.hash(token),
        str(m, "id"),
        Timestamp.from(Instant.now().plusSeconds(43200)));
    cookie(response, token);
    audit("LOGIN", str(m, "id"));
    return identity(m);
  }

  @Transactional
  public Map<String, Object> register(Map<String, Object> body) {
    String em = email(str(body, "email")),
        pw = secret(body),
        name = str(body, "name"),
        type = str(body, "type");
    password(pw);
    if (name.isBlank() || name.length() > 200 || !Set.of("PRIVATE", "COMPANY").contains(type))
      throw new HttpProblem(400, "Inserisci nome e tipo di account");
    if (!db.queryForList("SELECT id FROM accounts WHERE email=?", em).isEmpty())
      throw new HttpProblem(409, "Email già registrata");
    String company = str(body, "companyName"),
        vat = str(body, "vatNumber"),
        address = str(body, "address");
    if (type.equals("COMPANY")
        && (company.isBlank()
            || company.length() > 200
            || !vat.matches("[0-9]{11}")
            || address.isBlank()
            || address.length() > 300))
      throw new HttpProblem(400, "Inserisci ragione sociale, partita IVA di 11 cifre e indirizzo");
    String workspace = id(), user = id();
    db.update(
        "INSERT INTO workspaces(id,type,name,vat_number,address,status)"
            + " VALUES(?,?,?,?,?,'PENDING_PAYMENT')",
        workspace,
        type,
        type.equals("COMPANY") ? company : name,
        vat,
        address);
    db.update(
        "INSERT INTO accounts(id,workspace_id,email,name,role,password_hash,status,verified)"
            + " VALUES(?,?,?,?,?,?,'ACTIVE',?)",
        user,
        workspace,
        em,
        name,
        type.equals("COMPANY") ? "OWNER" : "PRIVATE",
        encoder.encode(pw),
        dev());
    String link = issue("VERIFY", user, workspace, em, name);
    audit("REGISTER", user);
    return dev()
        ? Map.of(
            "message", "Account creato. Accedi e scegli il piano di prova", "verificationUrl", link)
        : Map.of(
            "message",
            "Controlla la tua email per verificare l'account, poi accedi e scegli il piano");
  }

  String issue(String purpose, String user, String workspace, String em, String name) {
    String raw = Tokens.create();
    db.update(
        "INSERT INTO account_tokens(digest,user_id,workspace_id,email,name,purpose,expires_at)"
            + " VALUES(?,?,?,?,?,?,?)",
        Tokens.hash(raw),
        user,
        workspace,
        em,
        name,
        purpose,
        Timestamp.from(Instant.now().plusSeconds(purpose.equals("RESET") ? 3600 : 172800)));
    String path =
        switch (purpose) {
          case "RESET" -> "/reimposta-password";
          case "INVITE" -> "/invito";
          default -> "/verifica-email";
        };
    String link = env.getProperty("platform.public-url") + path + "?token=" + raw;
    if (!dev()) {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(env.getProperty("platform.mail-from"));
      message.setTo(em);
      message.setSubject("BollettaLAB — " + purpose);
      message.setText(
          "Ciao "
              + name
              + ",\ncontinua qui: "
              + link
              + "\nSe non hai richiesto questa operazione, ignora il messaggio.");
      mail.send(message);
    }
    return link;
  }

  Map<String, Object> consume(String raw, String purpose) {
    var rows =
        db.queryForList(
            "SELECT * FROM account_tokens WHERE digest=? AND purpose=? AND expires_at>? FOR UPDATE",
            Tokens.hash(raw),
            purpose,
            Timestamp.from(Instant.now()));
    if (rows.isEmpty()) throw new HttpProblem(400, "Link scaduto o non valido");
    var token = rows.get(0);
    db.update("DELETE FROM account_tokens WHERE digest=?", Tokens.hash(raw));
    return token;
  }

  @Transactional
  public void verify(String token) {
    var t = consume(token, "VERIFY");
    db.update("UPDATE accounts SET verified=TRUE WHERE id=?", str(t, "user_id"));
  }

  @Transactional
  public Map<String, Object> forgot(Map<String, Object> body) {
    var rows = db.queryForList("SELECT * FROM accounts WHERE email=?", email(str(body, "email")));
    String link = "";
    if (!rows.isEmpty()) {
      var u = rows.get(0);
      link = issue("RESET", str(u, "id"), str(u, "workspace_id"), str(u, "email"), str(u, "name"));
    }
    return dev() && !link.isBlank()
        ? Map.of("message", "Usa il link di prova per reimpostare la password", "resetUrl", link)
        : Map.of("message", "Se l'account esiste, riceverai un link per reimpostare la password");
  }

  @Transactional
  public void reset(Map<String, Object> body) {
    String pw = secret(body);
    password(pw);
    var t = consume(str(body, "token"), "RESET");
    db.update(
        "UPDATE accounts SET password_hash=? WHERE id=?", encoder.encode(pw), str(t, "user_id"));
    db.update("DELETE FROM sessions WHERE user_id=?", str(t, "user_id"));
    db.update("DELETE FROM account_tokens WHERE user_id=? AND purpose='RESET'", str(t, "user_id"));
    audit("RESET_PASSWORD", str(t, "user_id"));
  }

  @Transactional
  public Map<String, Object> invite(Map<String, Object> body, int seats) {
    RequestContext.owner();
    Identity owner = RequestContext.required();
    db.queryForMap("SELECT id FROM workspaces WHERE id=? FOR UPDATE", owner.workspaceId());
    Integer n =
        db.queryForObject(
            "SELECT COUNT(*) FROM accounts WHERE workspace_id=? AND role='AGENT'",
            Integer.class,
            owner.workspaceId());
    Integer pending =
        db.queryForObject(
            "SELECT COUNT(*) FROM account_tokens WHERE workspace_id=? AND purpose='INVITE' AND"
                + " expires_at>?",
            Integer.class,
            owner.workspaceId(),
            Timestamp.from(Instant.now()));
    if (n + pending >= seats)
      throw new HttpProblem(409, "Posti agente esauriti. Acquista posti aggiuntivi");
    String em = email(str(body, "email")), name = str(body, "name");
    if (name.isBlank() || name.length() > 200)
      throw new HttpProblem(400, "Inserisci il nome dell'agente");
    if (!db.queryForList("SELECT id FROM accounts WHERE email=?", em).isEmpty()
        || !db.queryForList(
                "SELECT digest FROM account_tokens WHERE workspace_id=? AND email=? AND"
                    + " purpose='INVITE' AND expires_at>?",
                owner.workspaceId(),
                em,
                Timestamp.from(Instant.now()))
            .isEmpty()) throw new HttpProblem(409, "Email già registrata o invitata");
    String link = issue("INVITE", null, owner.workspaceId(), em, name);
    audit("INVITE_AGENT", em);
    return dev()
        ? Map.of("message", "Invito creato", "inviteUrl", link)
        : Map.of("message", "Invito inviato via email");
  }

  @Transactional
  public void accept(Map<String, Object> body) {
    String pw = secret(body);
    password(pw);
    var t = consume(str(body, "token"), "INVITE");
    var w =
        db.queryForMap(
            "SELECT status FROM workspaces WHERE id=? FOR UPDATE", str(t, "workspace_id"));
    if (!"ACTIVE".equals(str(w, "status"))) throw new HttpProblem(403, "L'impresa non è attiva");
    db.update(
        "INSERT INTO accounts(id,workspace_id,email,name,role,password_hash,status,verified)"
            + " VALUES(?,?,?,?,'AGENT',?,'ACTIVE',TRUE)",
        id(),
        str(t, "workspace_id"),
        str(t, "email"),
        str(t, "name"),
        encoder.encode(pw));
  }

  @Transactional
  public void block(String user, boolean blocked, boolean admin) {
    Identity actor = RequestContext.required();
    var u = account(user);
    if ("DELETING".equals(str(u, "workspace_status")))
      throw new HttpProblem(409, "Eliminazione in corso: ripeti l’eliminazione per completarla");
    if (actor.id().equals(user)) throw new HttpProblem(400, "Non puoi bloccare il tuo account");
    if (admin) {
      RequestContext.admin();
      if ("ADMIN".equals(str(u, "role")))
        throw new HttpProblem(403, "Non puoi modificare un amministratore");
      if (!"AGENT".equals(str(u, "role")))
        db.update(
            "UPDATE workspaces SET status=? WHERE id=?",
            blocked ? "BLOCKED" : "ACTIVE",
            str(u, "workspace_id"));
    } else {
      RequestContext.owner();
      if (!actor.workspaceId().equals(str(u, "workspace_id")) || !"AGENT".equals(str(u, "role")))
        throw new HttpProblem(404, "Agente non trovato");
    }
    db.update("UPDATE accounts SET status=? WHERE id=?", blocked ? "BLOCKED" : "ACTIVE", user);
    db.update("DELETE FROM sessions WHERE user_id=?", user);
    audit(blocked ? "BLOCK_USER" : "UNBLOCK_USER", user);
  }

  @Bean
  ApplicationRunner bootstrapAdmin() {
    return args -> {
      String em = env.getProperty("platform.admin-email", "");
      String pw = env.getProperty("platform.admin-password", "");
      if (em.isBlank() || pw.isBlank()) return;
      password(pw);
      em = email(em);
      if (!db.queryForList("SELECT id FROM accounts WHERE email=?", em).isEmpty()) return;
      String w = id(), u = id();
      db.update(
          "INSERT INTO workspaces(id,type,name,status)"
              + " VALUES(?,'ADMIN','Amministrazione','ACTIVE')",
          w);
      db.update(
          "INSERT INTO accounts(id,workspace_id,email,name,role,password_hash,status,verified)"
              + " VALUES(?,?,?,'Amministratore','ADMIN',?,'ACTIVE',TRUE)",
          u,
          w,
          em,
          encoder.encode(pw));
    };
  }
}
