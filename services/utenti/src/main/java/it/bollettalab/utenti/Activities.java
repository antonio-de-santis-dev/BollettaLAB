package it.bollettalab.utenti;

import it.bollettalab.platform.*;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class Activities implements ActivitySink {
  private final JdbcTemplate db;

  public Activities(JdbcTemplate db) {
    this.db = db;
  }

  public void record(ActivityEvent event) {
    if (event == null
        || event.action() == null
        || event.description() == null
        || event.status() == null
        || !Set.of("COMPLETED", "WARNING", "ERROR").contains(event.status()))
      throw new HttpProblem(400, "Evento attività non valido");
    // Events queued by an older sender may still carry over-long values: store them cut.
    ActivityEvent e = event.bounded();
    if (e.eventId() == null || !e.eventId().matches("[a-fA-F0-9-]{36}"))
      throw new HttpProblem(400, "Identificativo evento non valido");
    try {
      db.update(
          "INSERT INTO"
              + " audit_log(id,workspace_id,actor_id,actor_name,actor_email,action,description,status,service,request_id,http_method,route,http_status)"
              + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
          e.eventId(),
          e.workspaceId(),
          e.actorId(),
          e.actorName(),
          e.actorEmail(),
          e.action(),
          e.description(),
          e.status(),
          e.service(),
          e.requestId(),
          e.method(),
          e.route(),
          e.httpStatus());
    } catch (org.springframework.dao.DuplicateKeyException duplicate) {
      /* A retry of an acknowledged batch is harmless. */
    }
  }

  public Map<String, Object> search(
      String search, String status, String service, String from, String to, int page, int size) {
    RequestContext.admin();
    if (page < 0
        || size < 1
        || size > 100
        || search.length() > 200
        || !status.isEmpty() && !Set.of("COMPLETED", "WARNING", "ERROR").contains(status))
      throw new HttpProblem(400, "Filtri attività non validi");
    var args = new ArrayList<Object>();
    var where = new StringBuilder(" WHERE 1=1");
    if (!search.isBlank()) {
      where.append(
          " AND (LOWER(COALESCE(l.actor_name,a.name,'')) LIKE ? OR"
              + " LOWER(COALESCE(l.actor_email,a.email,'')) LIKE ? OR LOWER(l.action) LIKE ? OR"
              + " LOWER(COALESCE(l.description,l.subject_id,'')) LIKE ?)");
      String pattern = "%" + search.toLowerCase(Locale.ROOT) + "%";
      for (int i = 0; i < 4; i++) args.add(pattern);
    }
    if (!status.isEmpty()) {
      where.append(" AND l.status=?");
      args.add(status);
    }
    if (!service.isEmpty()) {
      where.append(" AND l.service=?");
      args.add(service);
    }
    try {
      if (!from.isEmpty()) {
        where.append(" AND l.created_at>=?");
        args.add(
            Timestamp.from(
                LocalDate.parse(from).atStartOfDay(ZoneId.of("Europe/Rome")).toInstant()));
      }
      if (!to.isEmpty()) {
        where.append(" AND l.created_at<?");
        args.add(
            Timestamp.from(
                LocalDate.parse(to)
                    .plusDays(1)
                    .atStartOfDay(ZoneId.of("Europe/Rome"))
                    .toInstant()));
      }
    } catch (java.time.DateTimeException e) {
      throw new HttpProblem(400, "Data filtro non valida");
    }
    String join = " FROM audit_log l LEFT JOIN accounts a ON a.id=l.actor_id";
    long total = db.queryForObject("SELECT COUNT(*)" + join + where, Long.class, args.toArray());
    var pageArgs = new ArrayList<>(args);
    pageArgs.add(size);
    pageArgs.add((long) page * size);
    var rows =
        db.queryForList(
            "SELECT l.*,COALESCE(l.actor_name,a.name,'Sistema')"
                + " account_name,COALESCE(l.actor_email,a.email,'') account_email"
                + join
                + where
                + " ORDER BY l.created_at DESC,l.id DESC LIMIT ? OFFSET ?",
            pageArgs.toArray());
    return Map.of("items", rows, "total", total, "page", page, "size", size);
  }
}
