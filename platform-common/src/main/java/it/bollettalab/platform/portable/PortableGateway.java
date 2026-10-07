package it.bollettalab.platform.portable;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loopback-only web entry point for the Windows package; no installed web server required. */
public final class PortableGateway implements AutoCloseable {
  private final HttpServer server;
  private final ExecutorService executor = Executors.newFixedThreadPool(12);
  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(3))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();
  private final Path web;
  private final Map<String, URI> services;
  private final String token;
  private final String instance;
  private static final int MAX_BODY = 2 * 1024 * 1024;
  private static final Set<String> REQUEST_HEADERS =
      Set.of("content-type", "accept", "cookie", "x-requested-with", "x-idempotency-key");
  private static final Set<String> RESPONSE_HEADERS =
      Set.of("content-type", "content-disposition", "set-cookie", "cache-control", "retry-after");

  public PortableGateway(
      int port, Path web, Map<String, URI> services, String token, String instance)
      throws IOException {
    if (token.length() < 32 || !Files.isRegularFile(web.resolve("index.html")))
      throw new IllegalArgumentException("Incomplete portable configuration");
    this.web = web.toRealPath();
    this.services = Map.copyOf(services);
    this.token = token;
    this.instance = instance;
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 32);
    server.setExecutor(executor);
    server.createContext("/", this::handle);
    server.start();
  }

  public int port() {
    return server.getAddress().getPort();
  }

  private void handle(HttpExchange x) throws IOException {
    try {
      String host = x.getRequestHeaders().getFirst("Host");
      String expected = "127.0.0.1:" + port();
      String alternate = "localhost:" + port();
      if (!expected.equalsIgnoreCase(host) && !alternate.equalsIgnoreCase(host)) {
        send(x, 403, "text/plain", "Host non autorizzato".getBytes(StandardCharsets.UTF_8));
        return;
      }
      String origin = x.getRequestHeaders().getFirst("Origin");
      if (origin != null
          && !origin.equals("http://" + expected)
          && !origin.equals("http://" + alternate)) {
        send(x, 403, "text/plain", "Origine non autorizzata".getBytes(StandardCharsets.UTF_8));
        return;
      }
      x.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
      x.getResponseHeaders().set("Referrer-Policy", "strict-origin-when-cross-origin");
      x.getResponseHeaders()
          .set(
              "Content-Security-Policy",
              "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src"
                  + " 'self' data: blob:; font-src 'self'; connect-src 'self'; object-src 'none';"
                  + " base-uri 'self'; frame-ancestors 'none'; frame-src 'self' blob:");
      String path = x.getRequestURI().getPath();
      if (path.equals("/portable/status") && x.getRequestMethod().equals("GET")) {
        send(x, 200, "text/plain", instance.getBytes(StandardCharsets.UTF_8));
        return;
      }
      if (path.equals("/portable/shutdown") && x.getRequestMethod().equals("POST")) {
        String provided = x.getRequestHeaders().getFirst("X-Portable-Token");
        if (provided == null
            || !MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8))) {
          send(x, 403, "text/plain", new byte[0]);
          return;
        }
        send(x, 200, "text/plain", "Arresto".getBytes(StandardCharsets.UTF_8));
        new Thread(this::close, "portable-gateway-stop").start();
        return;
      }
      if (path.startsWith("/api/")) {
        proxy(x, path);
        return;
      }
      if (path.startsWith("/internal") || path.startsWith("/portable")) {
        send(x, 404, "text/plain", new byte[0]);
        return;
      }
      if (!Set.of("GET", "HEAD").contains(x.getRequestMethod())) {
        send(x, 405, "text/plain", new byte[0]);
        return;
      }
      Path file = web.resolve(path.substring(1)).normalize();
      if (!file.startsWith(web)) {
        send(x, 403, "text/plain", new byte[0]);
        return;
      }
      if (!Files.isRegularFile(file)) {
        if (path.startsWith("/assets/")) {
          send(x, 404, "text/plain", new byte[0]);
          return;
        }
        file = web.resolve("index.html");
      }
      if (!file.toRealPath().startsWith(web)) {
        send(x, 403, "text/plain", new byte[0]);
        return;
      }
      String name = file.getFileName().toString();
      String type =
          name.endsWith(".js")
              ? "text/javascript"
              : name.endsWith(".css")
                  ? "text/css"
                  : name.endsWith(".html")
                      ? "text/html"
                      : name.endsWith(".svg")
                          ? "image/svg+xml"
                          : name.endsWith(".png")
                              ? "image/png"
                              : name.endsWith(".woff2") ? "font/woff2" : "application/octet-stream";
      x.getResponseHeaders()
          .set("Cache-Control", name.equals("index.html") ? "no-store" : "public, max-age=3600");
      send(x, 200, type, Files.readAllBytes(file));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      send(x, 503, "text/plain", new byte[0]);
    } catch (Exception e) {
      send(x, 502, "text/plain", "Servizio non disponibile".getBytes(StandardCharsets.UTF_8));
    } finally {
      x.close();
    }
  }

  private void proxy(HttpExchange x, String path) throws IOException, InterruptedException {
    String[] parts = path.split("/", 4);
    URI base = parts.length >= 4 ? services.get(parts[2]) : null;
    if (base == null) {
      send(x, 404, "text/plain", new byte[0]);
      return;
    }
    byte[] body = x.getRequestBody().readNBytes(MAX_BODY + 1);
    if (body.length > MAX_BODY) {
      send(x, 413, "text/plain", new byte[0]);
      return;
    }
    String raw = x.getRequestURI().getRawPath();
    String tail = raw.substring(("/api/" + parts[2]).length());
    String query = x.getRequestURI().getRawQuery();
    URI uri = URI.create(base.toString() + "/api" + tail + (query == null ? "" : "?" + query));
    HttpRequest.Builder request =
        HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(60))
            .method(
                x.getRequestMethod(),
                body.length == 0
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofByteArray(body));
    x.getRequestHeaders()
        .forEach(
            (name, values) -> {
              if (REQUEST_HEADERS.contains(name.toLowerCase(java.util.Locale.ROOT)))
                values.forEach(v -> request.header(name, v));
            });
    request.header("X-Forwarded-For", "127.0.0.1");
    request.header("X-Forwarded-Proto", "http");
    request.header("X-Forwarded-Host", x.getRequestHeaders().getFirst("Host"));
    HttpResponse<byte[]> response =
        client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    response
        .headers()
        .map()
        .forEach(
            (name, values) -> {
              if (RESPONSE_HEADERS.contains(name.toLowerCase(java.util.Locale.ROOT)))
                values.forEach(v -> x.getResponseHeaders().add(name, v));
            });
    send(x, response.statusCode(), null, response.body());
  }

  private static void send(HttpExchange x, int status, String type, byte[] body)
      throws IOException {
    if (type != null)
      x.getResponseHeaders()
          .set("Content-Type", type + (type.startsWith("text/") ? "; charset=UTF-8" : ""));
    boolean noBody =
        x.getRequestMethod().equals("HEAD") || status == 204 || status == 304 || body.length == 0;
    x.sendResponseHeaders(status, noBody ? -1 : body.length);
    if (!noBody) x.getResponseBody().write(body);
  }

  @Override
  public void close() {
    server.stop(1);
    executor.shutdown();
  }

  public static void main(String[] args) throws Exception {
    Map<String, URI> services =
        Map.of(
            "utenti",
            URI.create("http://127.0.0.1:8101"),
            "pagamento",
            URI.create("http://127.0.0.1:8102"),
            "luce",
            URI.create("http://127.0.0.1:8103"),
            "luce-business",
            URI.create("http://127.0.0.1:8104"),
            "gas",
            URI.create("http://127.0.0.1:8105"));
    PortableGateway gateway =
        new PortableGateway(
            8091,
            Path.of("web"),
            services,
            System.getenv("PORTABLE_TOKEN"),
            System.getenv("PORTABLE_INSTANCE"));
    Runtime.getRuntime().addShutdownHook(new Thread(gateway::close));
    System.out.println("BollettaLAB pronto: http://127.0.0.1:8091");
  }
}
