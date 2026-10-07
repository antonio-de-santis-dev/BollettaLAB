package it.bollettalab.platform;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

public class InternalClient {
  private final RestClient client;

  public InternalClient(String key) {
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(3000);
    factory.setReadTimeout(15000);
    client =
        RestClient.builder().requestFactory(factory).defaultHeader("X-Internal-Key", key).build();
  }

  public <T> T get(String url, Class<T> type) {
    return get(url, "", type);
  }

  public <T> T get(String url, String cookie, Class<T> type) {
    try {
      return client.get().uri(url).header("Cookie", cookie).retrieve().body(type);
    } catch (RestClientResponseException e) {
      throw new HttpProblem(e.getStatusCode().value(), "Accesso non disponibile o non autorizzato");
    }
  }

  public <T> T post(String url, Object body, Class<T> type) {
    try {
      return client.post().uri(url).body(body).retrieve().body(type);
    } catch (RestClientResponseException e) {
      throw new HttpProblem(
          e.getStatusCode().value(),
          e.getStatusCode().value() == 402
              ? "Simulazioni esaurite: modifica il piano o acquista simulazioni"
              : "Operazione non disponibile");
    }
  }

  public void delete(String url) {
    client.delete().uri(url).retrieve().toBodilessEntity();
  }
}
