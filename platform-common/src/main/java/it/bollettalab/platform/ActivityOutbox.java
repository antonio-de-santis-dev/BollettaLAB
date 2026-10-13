package it.bollettalab.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestClient;

/** Persist locally and deliver batches later. Failed deliveries survive service restarts. */
public class ActivityOutbox implements ActivitySink {
  private final JdbcTemplate db;
  private final ObjectMapper json;
  private final RestClient client;
  private final String url;

  public ActivityOutbox(JdbcTemplate db, ObjectMapper json, String users, String key) {
    this.db = db;
    this.json = json;
    url = users + "/internal/audit/batch";
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(1000);
    factory.setReadTimeout(3000);
    client =
        RestClient.builder().requestFactory(factory).defaultHeader("X-Internal-Key", key).build();
  }

  @org.springframework.transaction.annotation.Transactional(
      propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
  public void record(ActivityEvent e) {
    try {
      db.update(
          "INSERT INTO activity_outbox(id,payload) VALUES(?,?)",
          e.eventId(),
          json.writeValueAsString(e.bounded()));
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalStateException("Evento attività non serializzabile", ex);
    }
  }

  @Scheduled(fixedDelayString = "${platform.audit.delivery-delay-ms:2000}")
  public void deliver() {
    var rows =
        db.queryForList("SELECT id,payload FROM activity_outbox ORDER BY created_at,id LIMIT 100");
    if (rows.isEmpty()) return;
    var events = new ArrayList<ActivityEvent>();
    var ids = new ArrayList<Object>();
    for (var row : rows)
      try {
        events.add(json.readValue(row.get("payload").toString(), ActivityEvent.class).bounded());
        ids.add(row.get("id"));
      } catch (com.fasterxml.jackson.core.JsonProcessingException unreadable) {
        // It can never be delivered; keeping it would block every later event.
        LoggerFactory.getLogger(getClass())
            .error("Activity event {} unreadable: discarded", row.get("id"));
        db.update("DELETE FROM activity_outbox WHERE id=?", row.get("id"));
      }
    if (events.isEmpty()) return;
    try {
      client.post().uri(url).body(events).retrieve().toBodilessEntity();
      for (var id : ids) db.update("DELETE FROM activity_outbox WHERE id=?", id);
    } catch (org.springframework.web.client.RestClientException e) {
      LoggerFactory.getLogger(getClass())
          .warn("Activity delivery pending: {} events retained locally", events.size());
    }
  }
}
