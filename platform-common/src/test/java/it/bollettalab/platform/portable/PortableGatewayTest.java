package it.bollettalab.platform.portable;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PortableGatewayTest {
  @TempDir Path web;
  private final HttpClient client = HttpClient.newHttpClient();
  private static final String TOKEN = "0123456789012345678901234567890123456789";

  private HttpResponse<String> get(PortableGateway g, String path) throws Exception {
    return client.send(
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + g.port() + path)).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void servesDeepLinksAndJavascriptButHidesInternalRoutesAndMissingAssets() throws Exception {
    Files.writeString(web.resolve("index.html"), "<html>BollettaLAB</html>");
    Files.createDirectory(web.resolve("assets"));
    Files.writeString(web.resolve("assets/test.js"), "export const value=1;");
    try (var g = new PortableGateway(0, web, Map.of(), TOKEN, "instance-1")) {
      assertEquals("<html>BollettaLAB</html>", get(g, "/simulatori/gas").body());
      assertTrue(
          get(g, "/assets/test.js")
              .headers()
              .firstValue("content-type")
              .orElseThrow()
              .startsWith("text/javascript"));
      assertEquals(404, get(g, "/assets/missing.js").statusCode());
      assertEquals(404, get(g, "/internal/identity").statusCode());
      assertEquals("instance-1", get(g, "/portable/status").body());
    }
  }

  @Test
  void forwardsCookiesIdempotencyUtf8AndPdfHeadersWithoutForwardingInternalKey() throws Exception {
    Files.writeString(web.resolve("index.html"), "BollettaLAB");
    var backend = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    backend.createContext(
        "/api/confronti",
        x -> {
          assertEquals("BL_SESSION=test", x.getRequestHeaders().getFirst("Cookie"));
          assertEquals("operation-1", x.getRequestHeaders().getFirst("X-Idempotency-Key"));
          assertNull(x.getRequestHeaders().getFirst("X-Internal-Key"));
          assertEquals("127.0.0.1", x.getRequestHeaders().getFirst("X-Forwarded-For"));
          x.getResponseHeaders()
              .add("Set-Cookie", "BL_SESSION=new; HttpOnly; SameSite=Strict; Path=/");
          x.getResponseHeaders().add("Content-Disposition", "attachment; filename=test.pdf");
          x.getResponseHeaders().add("Content-Type", "application/pdf");
          byte[] body = "PDF già creato €".getBytes(StandardCharsets.UTF_8);
          x.sendResponseHeaders(201, body.length);
          x.getResponseBody().write(body);
          x.close();
        });
    backend.start();
    try (var g =
        new PortableGateway(
            0,
            web,
            Map.of("gas", URI.create("http://127.0.0.1:" + backend.getAddress().getPort())),
            TOKEN,
            "test")) {
      var request =
          HttpRequest.newBuilder(
                  URI.create("http://127.0.0.1:" + g.port() + "/api/gas/confronti?q=1"))
              .header("Cookie", "BL_SESSION=test")
              .header("X-Idempotency-Key", "operation-1")
              .header("X-Internal-Key", "attacker")
              .header("X-Forwarded-For", "attacker")
              .POST(HttpRequest.BodyPublishers.ofString("{}"))
              .build();
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      assertEquals(201, response.statusCode());
      assertEquals("PDF già creato €", response.body());
      assertTrue(response.headers().firstValue("set-cookie").orElseThrow().contains("HttpOnly"));
      assertTrue(
          response.headers().firstValue("content-disposition").orElseThrow().contains("test.pdf"));
      assertEquals(404, get(g, "/api/unknown/path").statusCode());
    } finally {
      backend.stop(0);
    }
  }

  @Test
  void rejectsCrossOriginMutationLargePayloadAndWrongShutdownToken() throws Exception {
    Files.writeString(web.resolve("index.html"), "BollettaLAB");
    try (var g =
        new PortableGateway(
            0, web, Map.of("gas", URI.create("http://127.0.0.1:1")), TOKEN, "test")) {
      String base = "http://127.0.0.1:" + g.port();
      var foreign =
          HttpRequest.newBuilder(URI.create(base + "/api/gas/confronti"))
              .header("Origin", "https://other.example")
              .POST(HttpRequest.BodyPublishers.ofString("{}"))
              .build();
      assertEquals(403, client.send(foreign, HttpResponse.BodyHandlers.discarding()).statusCode());
      var large =
          HttpRequest.newBuilder(URI.create(base + "/api/gas/confronti"))
              .POST(HttpRequest.BodyPublishers.ofByteArray(new byte[2 * 1024 * 1024 + 1]))
              .build();
      assertEquals(413, client.send(large, HttpResponse.BodyHandlers.discarding()).statusCode());
      var stop =
          HttpRequest.newBuilder(URI.create(base + "/portable/shutdown"))
              .POST(HttpRequest.BodyPublishers.noBody())
              .build();
      assertEquals(403, client.send(stop, HttpResponse.BodyHandlers.discarding()).statusCode());
      assertEquals(200, get(g, "/portable/status").statusCode());
    }
  }
}
