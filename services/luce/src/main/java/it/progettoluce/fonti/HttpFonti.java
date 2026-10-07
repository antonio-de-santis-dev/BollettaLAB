package it.progettoluce.fonti;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class HttpFonti {
  static final int MAX = 12 * 1024 * 1024;

  public byte[] get(String url) {
    return richiesta(url, null, null);
  }

  public byte[] post(String url, byte[] body, String token) {
    return richiesta(url, body, token);
  }

  private byte[] richiesta(String url, byte[] body, String token) {
    try {
      URI uri = URI.create(url);
      for (int redirect = 0; redirect < 3; redirect++) {
        if (!"https".equals(uri.getScheme())
            || !Set.of("www.ilportaleofferte.it", "api.mercatoelettrico.org")
                .contains(uri.getHost())
            || uri.getUserInfo() != null
            || uri.getPort() != -1)
          throw new IllegalArgumentException("Indirizzo fonte non consentito");
        var c = (HttpURLConnection) uri.toURL().openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(15000);
        c.setInstanceFollowRedirects(false);
        c.setRequestProperty("User-Agent", "ProgettoLuce/1.0");
        try {
          if (body != null) {
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            if (token != null) c.setRequestProperty("Authorization", "Bearer " + token);
            try (var out = c.getOutputStream()) {
              out.write(body);
            }
          }
          int status = c.getResponseCode();
          if (body == null && Set.of(301, 302, 303, 307, 308).contains(status)) {
            uri = uri.resolve(c.getHeaderField("Location"));
            continue;
          }
          if (status != 200)
            throw new IllegalArgumentException("La fonte ha risposto HTTP " + status);
          try (var in = c.getInputStream()) {
            var bytes = in.readNBytes(MAX + 1);
            if (bytes.length > MAX)
              throw new IllegalArgumentException("Risposta fonte troppo grande");
            return bytes;
          }
        } finally {
          c.disconnect();
        }
      }
      throw new IllegalArgumentException("Troppi reindirizzamenti della fonte");
    } catch (IOException e) {
      throw new IllegalArgumentException("Fonte non raggiungibile o tempo di attesa scaduto");
    }
  }

  static String testo(byte[] bytes) {
    return new String(bytes, StandardCharsets.UTF_8).replace("\uFEFF", "");
  }
}
