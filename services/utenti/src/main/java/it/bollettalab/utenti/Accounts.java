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
  // Compared when the email is unknown, so a failed login always costs one BCrypt check.
  final String missingHash = encoder.encode(Tokens.create());
  static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Accounts.class);

  Accounts(JdbcTemplate db, Environment env, JavaMailSender mail) {
    this.db = db;
    this.env = env;
    this.mail = mail;
    if (env.matchesProfiles("dev") && !loopback(env.getProperty("platform.public-url", "")))
      throw new IllegalStateException(
          "Il profilo dev mostra i link di recupero password: usalo solo con PUBLIC_URL su"
              + " localhost o 127.0.0.1");
  }

  static boolean loopback(String url) {
    try {
      String host = java.net.URI.create(url).getHost();
      return host != null && Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(host);
    } catch (IllegalArgumentException e) {
      return false;
    }
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
    audit(action, subject, RequestContext.identity());
  }

  private void audit(String action, String subject, Identity i) {
    db.update(
        "INSERT INTO"
            + " audit_log(id,workspace_id,actor_id,action,subject_id,actor_name,actor_email,description)"
            + " VALUES(?,?,?,?,?,?,?,?)",
        id(),
        i == null ? null : i.workspaceId(),
        i == null ? null : i.id(),
        action,
        subject,
        i == null ? null : i.name(),
        i == null ? null : i.email(),
        action.replace("_", " ") + " · " + subject);
  }

  Map<String, Object> account(String id) {
    var rows =
        db.queryForList(
            "SELECT a.*,w.status workspace_status,w.name company_name,w.vat_number,w.address,w.type"
                + " workspace_type FROM accounts a JOIN workspaces w ON w.id=a.workspace_id WHERE"
                + " a.id=?",
            id);
    if (rows.isEmpty()) throw new HttpProblem(404, "Account non trovato");
    return rows.get(0);
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
    throttle("auth", ip, 15);
  }

  /** At most {@code max} attempts per action and IP address in 15 minutes. */
  void throttle(String action, String ip, int max) {
    Instant now = Instant.now();
    attempts
        .entrySet()
        .removeIf(e -> e.getValue().stream().allMatch(t -> t.isBefore(now.minusSeconds(900))));
    List<Instant> times =
        attempts.computeIfAbsent(
            action + ":" + ip, k -> Collections.synchronizedList(new ArrayList<>()));
    synchronized (times) {
      times.removeIf(t -> t.isBefore(now.minusSeconds(900)));
      if (times.size() >= max)
        throw new HttpProblem(429, "Troppi tentativi. Riprova tra 15 minuti");
      times.add(now);
    }
  }

  @Transactional
  public Identity login(
      Map<String, Object> body, HttpServletRequest request, HttpServletResponse response) {
    throttle(request.getRemoteAddr());
    String em = email(str(body, "email"));
    var rows = db.queryForList("SELECT id,password_hash,status FROM accounts WHERE email=?", em);
    String hash = rows.isEmpty() ? missingHash : str(rows.get(0), "password_hash");
    if (!encoder.matches(secret(body), hash) || rows.isEmpty())
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
    request.setAttribute("lab.audit.identity", identity(m));
    audit("LOGIN", str(m, "id"), identity(m));
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
    try {
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
    } catch (org.springframework.dao.DuplicateKeyException e) {
      throw new HttpProblem(409, "Email già registrata");
    }
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
      try {
        link =
            issue("RESET", str(u, "id"), str(u, "workspace_id"), str(u, "email"), str(u, "name"));
      } catch (org.springframework.mail.MailException e) {
        // Same answer as for an unknown email: a mail failure must not reveal the account.
        log.warn("Invio email di recupero non riuscito", e);
        link = "";
      }
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

  /** Creates an immediately visible agent; the generated secret is returned only once. */
  @Transactional
  public Map<String, Object> createAgent(Map<String, Object> body, int seats) {
    RequestContext.owner();
    Identity owner = RequestContext.required();
    db.queryForMap("SELECT id FROM workspaces WHERE id=? FOR UPDATE", owner.workspaceId());
    String em = email(str(body, "email")), name = str(body, "name");
    if (name.isBlank() || name.length() > 200)
      throw new HttpProblem(400, "Inserisci il nome dell'agente");
    int occupied =
        db.queryForObject(
            "SELECT COUNT(*) FROM accounts WHERE workspace_id=? AND role='AGENT'",
            Integer.class,
            owner.workspaceId());
    int pending =
        db.queryForObject(
            "SELECT COUNT(*) FROM account_tokens WHERE workspace_id=? AND purpose='INVITE' AND"
                + " expires_at>? AND email<>?",
            Integer.class,
            owner.workspaceId(),
            Timestamp.from(Instant.now()),
            em);
    if (occupied + pending >= seats)
      throw new HttpProblem(409, "Posti agente esauriti. Acquista posti aggiuntivi");
    String password = Tokens.create(), agent = id();
    try {
      db.update(
          "INSERT INTO accounts(id,workspace_id,email,name,role,password_hash,status,verified)"
              + " VALUES(?,?,?,?,'AGENT',?,'ACTIVE',TRUE)",
          agent,
          owner.workspaceId(),
          em,
          name,
          encoder.encode(password));
    } catch (org.springframework.dao.DuplicateKeyException e) {
      throw new HttpProblem(409, "Email già registrata");
    }
    db.update(
        "DELETE FROM account_tokens WHERE workspace_id=? AND email=? AND purpose='INVITE'",
        owner.workspaceId(),
        em);
    audit("CREATE_AGENT", agent);
    return Map.of(
        "message",
        "Agente creato e pronto per l'accesso. Conserva la password: viene mostrata soltanto ora.",
        "email",
        em,
        "temporaryPassword",
        password);
  }

  @Transactional
  public Map<String, Object> resetAgent(String id) {
    RequestContext.owner();
    Identity owner = RequestContext.required();
    var agent = account(id);
    if (!owner.workspaceId().equals(str(agent, "workspace_id"))
        || !"AGENT".equals(str(agent, "role"))) throw new HttpProblem(404, "Agente non trovato");
    String password = Tokens.create();
    db.update("UPDATE accounts SET password_hash=? WHERE id=?", encoder.encode(password), id);
    db.update("DELETE FROM sessions WHERE user_id=?", id);
    db.update("DELETE FROM account_tokens WHERE user_id=?", id);
    audit("OWNER_RESET_AGENT_PASSWORD", id);
    return Map.of(
        "message",
        "Password reimpostata. Le sessioni precedenti sono state revocate.",
        "email",
        str(agent, "email"),
        "temporaryPassword",
        password);
  }

  public Map<String, Object> profile() {
    var a = account(RequestContext.required().id());
    return Map.of(
        "name",
        str(a, "name"),
        "email",
        str(a, "email"),
        "role",
        str(a, "role"),
        "companyName",
        str(a, "company_name"),
        "vatNumber",
        str(a, "vat_number"),
        "address",
        str(a, "address"));
  }

  @Transactional
  public Map<String, Object> updateProfile(Map<String, Object> body) {
    Identity actor = RequestContext.required();
    var a = account(actor.id());
    String name = str(body, "name"), em = email(str(body, "email"));
    if (name.isBlank() || name.length() > 200)
      throw new HttpProblem(400, "Inserisci un nome di massimo 200 caratteri");
    boolean emailChanged = !em.equals(str(a, "email"));
    String nextPassword = Objects.toString(body.get("newPassword"), "");
    if (emailChanged && !nextPassword.isEmpty())
      throw new HttpProblem(
          400, "Modifica prima l’email e verificala, poi cambia la password separatamente");
    if (emailChanged || !nextPassword.isEmpty()) {
      if (!encoder.matches(
          Objects.toString(body.get("currentPassword"), ""), str(a, "password_hash")))
        throw new HttpProblem(400, "La password attuale non è corretta");
    }
    if (!nextPassword.isEmpty()) password(nextPassword);
    try {
      db.update("UPDATE accounts SET name=?,email=? WHERE id=?", name, em, actor.id());
    } catch (org.springframework.dao.DuplicateKeyException e) {
      throw new HttpProblem(409, "Email già utilizzata da un altro account");
    }
    if (actor.owner()) {
      String company = str(body, "companyName"),
          vat = str(body, "vatNumber"),
          address = str(body, "address");
      if (company.isBlank()
          || company.length() > 200
          || !vat.matches("[0-9]{11}")
          || address.isBlank()
          || address.length() > 300)
        throw new HttpProblem(400, "Controlla ragione sociale, partita IVA (11 cifre) e indirizzo");
      db.update(
          "UPDATE workspaces SET name=?,vat_number=?,address=? WHERE id=?",
          company,
          vat,
          address,
          actor.workspaceId());
    }
    if (!nextPassword.isEmpty()) {
      db.update(
          "UPDATE accounts SET password_hash=? WHERE id=?",
          encoder.encode(nextPassword),
          actor.id());
      db.update("DELETE FROM sessions WHERE user_id=?", actor.id());
      db.update("DELETE FROM account_tokens WHERE user_id=? AND purpose='RESET'", actor.id());
    }
    if (emailChanged) {
      db.update("UPDATE accounts SET verified=FALSE WHERE id=?", actor.id());
      String link = issue("VERIFY", actor.id(), actor.workspaceId(), em, name);
      audit("UPDATE_PROFILE", actor.id());
      var result = new HashMap<String, Object>();
      result.put("message", "Dati aggiornati. Verifica la nuova email prima di nuovi acquisti.");
      result.put("loginRequired", !nextPassword.isEmpty());
      if (dev()) result.put("verificationUrl", link);
      return result;
    }
    audit("UPDATE_PROFILE", actor.id());
    return Map.of(
        "message",
        nextPassword.isEmpty() ? "Profilo aggiornato" : "Password aggiornata. Accedi nuovamente.",
        "loginRequired",
        !nextPassword.isEmpty());
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
    try {
      db.update(
          "INSERT INTO accounts(id,workspace_id,email,name,role,password_hash,status,verified)"
              + " VALUES(?,?,?,?,'AGENT',?,'ACTIVE',TRUE)",
          id(),
          str(t, "workspace_id"),
          str(t, "email"),
          str(t, "name"),
          encoder.encode(pw));
    } catch (org.springframework.dao.DuplicateKeyException e) {
      throw new HttpProblem(409, "Email già registrata");
    }
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
