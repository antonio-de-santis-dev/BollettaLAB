package it.bollettalab.platform;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ActivityOutboxTest {
  @Test
  void failedDeliveryRetainsPersistentEventsAndSuccessfulDeliveryAcknowledgesThem()
      throws Exception {
    var db = mock(JdbcTemplate.class);
    var json = new ObjectMapper();
    var event =
        new ActivityEvent(
            "w",
            "a",
            "Mario",
            "m@test.it",
            "Calcolo",
            "Confronto",
            "COMPLETED",
            "gas",
            null,
            "POST",
            "/api/confronti",
            201);
    String payload = json.writeValueAsString(event);
    when(db.queryForList(anyString()))
        .thenReturn(List.of(Map.of("id", event.eventId(), "payload", payload)));
    var unavailable = new ActivityOutbox(db, json, "http://127.0.0.1:1", "internal-test");
    unavailable.record(event);
    unavailable.deliver();
    verify(db)
        .update("INSERT INTO activity_outbox(id,payload) VALUES(?,?)", event.eventId(), payload);
    verify(db, never()).update(eq("DELETE FROM activity_outbox WHERE id=?"), anyString());
    var server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var received = new ArrayList<String>();
    server.createContext(
        "/internal/audit/batch",
        exchange -> {
          received.add(
              new String(
                  exchange.getRequestBody().readAllBytes(),
                  java.nio.charset.StandardCharsets.UTF_8));
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    server.start();
    try {
      new ActivityOutbox(
              db, json, "http://127.0.0.1:" + server.getAddress().getPort(), "internal-test")
          .deliver();
      verify(db).update("DELETE FROM activity_outbox WHERE id=?", event.eventId());
      assertThat(received).hasSize(1);
      assertThat(received.get(0)).contains(event.eventId(), "Mario");
    } finally {
      server.stop(0);
    }
  }
}
