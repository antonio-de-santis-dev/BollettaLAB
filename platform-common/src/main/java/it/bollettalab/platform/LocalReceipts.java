package it.bollettalab.platform;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "platform.simulator",
    havingValue = "true")
@Component
public class LocalReceipts {
  static final ThreadLocal<String> operation = new ThreadLocal<>();
  final JdbcTemplate db;
  final InternalClient client;
  final String payments;
  final com.fasterxml.jackson.databind.ObjectMapper json;

  public LocalReceipts(
      JdbcTemplate db,
      InternalClient client,
      @Value("${platform.payment-url:http://pagamento:8080}") String payments,
      com.fasterxml.jackson.databind.ObjectMapper json) {
    this.db = db;
    this.client = client;
    this.payments = payments;
    this.json = json;
  }

  public void record(long resultId, Object response) {
    String id = operation.get();
    if (id != null) {
      try {
        db.update(
            "INSERT INTO local_receipts(id,workspace_id,result_id,state,response_json,created_at)"
                + " VALUES(?,?,?,'SAVED',?,?)",
            id,
            RequestContext.workspace(),
            resultId,
            json.writeValueAsString(response),
            Timestamp.from(Instant.now()));
      } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  public void complete(String id, long result, String response) {
    db.update("UPDATE local_receipts SET response_json=? WHERE id=?", response, id);
    client.post(
        payments + "/internal/reservations/" + id + "/complete",
        Map.of("resultId", result, "response", response),
        Map.class);
    db.update("UPDATE local_receipts SET state='COMPLETE' WHERE id=?", id);
  }

  @Scheduled(fixedDelay = 30000)
  public void reconcile() {
    try {
      for (var r :
          db.queryForList(
              "SELECT id,result_id,response_json FROM local_receipts WHERE state='SAVED' AND"
                  + " response_json IS NOT NULL")) {
        try {
          complete(
              r.get("id").toString(),
              ((Number) r.get("result_id")).longValue(),
              r.get("response_json").toString());
        } catch (RuntimeException ignored) {
        }
      }
    } catch (RuntimeException ignored) {
    }
  }
}
