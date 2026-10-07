package it.progettoluce.fonti;

import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.math.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GmeClient {
  public static final String BASE = "https://api.mercatoelettrico.org/request";
  private final HttpFonti http;
  private final ObjectMapper mapper;
  private final String login, password;

  public GmeClient(
      HttpFonti http,
      ObjectMapper mapper,
      @Value("${fonti.gme.login:}") String login,
      @Value("${fonti.gme.password:}") String password) {
    this.http = http;
    this.mapper = mapper;
    this.login = login;
    this.password = password;
  }

  public boolean configurato() {
    return !login.isBlank() && !password.isBlank();
  }

  public List<ArchivioFonti.Importato> scarica(YearMonth mese) {
    if (!configurato())
      throw new IllegalArgumentException("GME richiede GME_LOGIN e GME_PASSWORD nel backend");
    if (!mese.isBefore(YearMonth.now(ZoneId.of("Europe/Rome"))))
      throw new IllegalArgumentException("Il PUN mensile richiede un mese concluso");
    try {
      JsonNode auth =
          mapper.readTree(
              http.post(
                  BASE + "/api/v1/Auth",
                  mapper.writeValueAsBytes(Map.of("Login", login, "Password", password)),
                  null));
      if (!auth.path("Success").asBoolean() || auth.path("token").asText().isBlank())
        throw new IllegalArgumentException(
            "Autenticazione GME non riuscita: controlla le credenziali sul server");
      var request =
          Map.of(
              "Platform",
              "PublicMarketResults",
              "Segment",
              "MGP",
              "DataName",
              "ME_ZonalPrices",
              "IntervalStart",
              Integer.parseInt(mese.atDay(1).format(DateTimeFormatter.BASIC_ISO_DATE)),
              "IntervalEnd",
              Integer.parseInt(mese.atEndOfMonth().format(DateTimeFormatter.BASIC_ISO_DATE)),
              "Attributes",
              mese.isBefore(YearMonth.of(2025, 10)) ? Map.of() : Map.of("GranularityType", "PT60"));
      var envelope =
          mapper.readTree(
              http.post(
                  BASE + "/api/v1/RequestData",
                  mapper.writeValueAsBytes(request),
                  auth.path("token").asText()));
      if (!envelope.path("FormatType").asText().equals(".json.zip"))
        throw new IllegalArgumentException("Risposta GME non disponibile nel formato atteso");
      return aggrega(decodifica(envelope.path("ContentResponse").asText()), mese);
    } catch (IOException e) {
      throw new IllegalArgumentException("Risposta GME non leggibile");
    }
  }

  JsonNode decodifica(String base64) throws IOException {
    byte[] packed;
    try {
      packed = Base64.getDecoder().decode(base64);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Risposta GME non valida");
    }
    if (packed.length > HttpFonti.MAX)
      throw new IllegalArgumentException("Archivio GME troppo grande");
    try (var zip = new ZipInputStream(new ByteArrayInputStream(packed))) {
      var entry = zip.getNextEntry();
      if (entry == null || entry.isDirectory() || !entry.getName().endsWith(".json"))
        throw new IllegalArgumentException("Archivio GME senza dati JSON");
      var data = zip.readNBytes(HttpFonti.MAX + 1);
      if (data.length > HttpFonti.MAX || zip.getNextEntry() != null)
        throw new IllegalArgumentException("Archivio GME inatteso o troppo grande");
      return mapper.readTree(data);
    }
  }

  public List<ArchivioFonti.Importato> aggrega(JsonNode records, YearMonth mese) {
    if (!records.isArray()) throw new IllegalArgumentException("Formato dati GME cambiato");
    Map<String, BigDecimal> sums = new HashMap<>();
    Map<String, Integer> counts = new HashMap<>();
    var seen = new HashSet<String>();
    ZoneId zone = ZoneId.of("Europe/Rome");
    for (var r : records) {
      if (!r.path("Zone").asText().equals("PUN") || !r.path("Market").asText().equals("MGP"))
        continue;
      LocalDate date;
      try {
        date = LocalDate.parse(r.path("FlowDate").asText(), DateTimeFormatter.BASIC_ISO_DATE);
      } catch (Exception e) {
        throw new IllegalArgumentException("Data GME non riconosciuta");
      }
      if (!YearMonth.from(date).equals(mese))
        throw new IllegalArgumentException("Dati GME fuori dal mese richiesto");
      int
          hours =
              (int)
                  Duration.between(date.atStartOfDay(zone), date.plusDays(1).atStartOfDay(zone))
                      .toHours(),
          hour = r.path("Hour").asInt();
      if (hour < 1 || hour > hours || !seen.add(date + ":" + hour))
        throw new IllegalArgumentException("Ore GME duplicate o non riconosciute");
      BigDecimal price;
      try {
        price = new BigDecimal(r.path("Price").asText());
      } catch (Exception e) {
        throw new IllegalArgumentException("Prezzo GME non riconosciuto");
      }
      if (price.abs().compareTo(new BigDecimal("1000000")) > 0)
        throw new IllegalArgumentException("Prezzo GME fuori intervallo");
      int localHour = date.atStartOfDay(zone).plusHours(hour - 1).getHour();
      String fascia = fascia(date, localHour);
      for (String key :
          fascia.equals("F1") ? List.of("F0", fascia) : List.of("F0", fascia, "F23")) {
        sums.merge(key, price, BigDecimal::add);
        counts.merge(key, 1, Integer::sum);
      }
    }
    int expected =
        (int)
            Duration.between(
                    mese.atDay(1).atStartOfDay(zone),
                    mese.plusMonths(1).atDay(1).atStartOfDay(zone))
                .toHours();
    if (seen.size() != expected)
      throw new IllegalArgumentException(
          "PUN mensile incompleto: ricevute " + seen.size() + " ore, attese " + expected);
    var out = new ArrayList<ArchivioFonti.Importato>();
    for (String f : List.of("F0", "F1", "F2", "F3", "F23")) {
      if (!counts.containsKey(f)) throw new IllegalArgumentException("Fascia GME mancante");
      BigDecimal v =
          sums.get(f)
              .divide(
                  BigDecimal.valueOf(counts.get(f)).multiply(new BigDecimal("1000")),
                  8,
                  RoundingMode.HALF_UP);
      CatalogoFonti.valida("PUN_" + f, mese.toString(), CatalogoFonti.INDICE, v);
      out.add(
          new ArchivioFonti.Importato(
              "PUN_" + f, mese.toString(), CatalogoFonti.INDICE, v, "GME", BASE, null));
    }
    return out;
  }

  static String fascia(LocalDate d, int h) {
    boolean festivo =
        d.getDayOfWeek() == DayOfWeek.SUNDAY
            || Set.of(
                    "01-01", "01-06", "04-25", "05-01", "06-02", "08-15", "11-01", "12-08", "12-25",
                    "12-26")
                .contains(d.format(DateTimeFormatter.ofPattern("MM-dd")))
            || d.equals(pasqua(d.getYear()).plusDays(1));
    if (festivo) return "F3";
    if (d.getDayOfWeek() != DayOfWeek.SATURDAY && h >= 8 && h < 19) return "F1";
    if (h >= 7 && h < 23) return "F2";
    return "F3";
  }

  static LocalDate pasqua(int y) {
    int a = y % 19,
        b = y / 100,
        c = y % 100,
        d = b / 4,
        e = b % 4,
        f = (b + 8) / 25,
        g = (b - f + 1) / 3,
        h = (19 * a + b - d - g + 15) % 30,
        i = c / 4,
        k = c % 4,
        l = (32 + 2 * e + 2 * i - h - k) % 7,
        m = (a + 11 * h + 22 * l) / 451;
    int n = h + l - 7 * m + 114;
    return LocalDate.of(y, n / 31, n % 31 + 1);
  }
}
