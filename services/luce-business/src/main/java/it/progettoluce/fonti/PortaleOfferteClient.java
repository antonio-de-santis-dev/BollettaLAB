package it.progettoluce.fonti;

import static it.progettoluce.fonti.CatalogoFonti.*;

import java.io.*;
import java.math.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.apache.commons.csv.*;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

@Component
public class PortaleOfferteClient {
  public static final String PAGINA =
      "https://www.ilportaleofferte.it/portaleOfferte/it/open-data.page";
  private final HttpFonti http;

  public PortaleOfferteClient(HttpFonti http) {
    this.http = http;
  }

  public List<ArchivioFonti.Importato> scarica() {
    var doc = Jsoup.parse(HttpFonti.testo(http.get(PAGINA)), PAGINA);
    var link =
        doc.select("a[href]").stream()
            .filter(
                a ->
                    a.attr("href").contains("/parametriML/")
                        && a.attr("href").matches(".*PO_Parametri_Mercato_Libero_E_[0-9]{8}\\.csv"))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Link ai parametri elettrici non trovato nella pagina ufficiale"));
    String url = link.absUrl("href");
    var match = java.util.regex.Pattern.compile("_([0-9]{8})\\.csv$").matcher(url);
    if (!match.find()) throw new IllegalArgumentException("Data della pubblicazione mancante");
    LocalDate data = LocalDate.parse(match.group(1), DateTimeFormatter.BASIC_ISO_DATE);
    var risultati = new ArrayList<>(parametri(HttpFonti.testo(http.get(url)), data, url));
    var punLink =
        doc.select("a[href]").stream()
            .filter(a -> a.text().contains("Prezzi storici") && a.attr("href").endsWith(".csv"))
            .findFirst()
            .orElseThrow(
                () -> new IllegalArgumentException("Link agli indici storici non trovato"));
    risultati.addAll(
        pun(HttpFonti.testo(http.get(punLink.absUrl("href"))), punLink.absUrl("href")));
    return risultati;
  }

  public List<ArchivioFonti.Importato> parametri(String csv, LocalDate data, String url) {
    var valori = new LinkedHashMap<String, BigDecimal>();
    try (var parser =
        CSVFormat.DEFAULT
            .builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .get()
            .parse(new StringReader(csv))) {
      if (!parser
          .getHeaderMap()
          .keySet()
          .containsAll(Set.of("nome_parametro", "valore", "descrizione")))
        throw new IllegalArgumentException("Formato CSV parametri cambiato");
      for (var row : parser) {
        String key = row.get("nome_parametro").trim();
        if (!key.matches("[a-z0-9_]{1,80}")
            || valori.putIfAbsent(key, numero(row.get("valore"))) != null)
          throw new IllegalArgumentException("Parametro duplicato o non riconosciuto");
      }
    } catch (IOException e) {
      throw new IllegalArgumentException("CSV parametri non leggibile");
    }
    String periodo = YearMonth.from(data).toString();
    var out = new ArrayList<ArchivioFonti.Importato>();
    for (var x : valori.entrySet())
      out.add(
          new ArchivioFonti.Importato(
              x.getKey(), periodo, ORIGINALE, x.getValue(), "PORTALE_OFFERTE", url, data));
    // Mapping follows AU's published calculation rules. CdispD is on NET consumption;
    // our engine charges dispatch on consumption including losses, hence the conversion.
    var lambda = obbligatorio(valori, "lambda");
    for (String categoria : List.of(RESIDENTE, NON_RESIDENTE)) {
      Map<String, BigDecimal> v = new LinkedHashMap<>();
      v.put("coefficientePerdite", lambda);
      v.put(
          "dispacciamentoKwh",
          obbligatorio(valori, "cdispd")
              .divide(BigDecimal.ONE.add(lambda), 8, RoundingMode.HALF_UP));
      v.put(
          "trasportoFissoMese",
          obbligatorio(valori, "sigma1").divide(new BigDecimal("12"), 8, RoundingMode.HALF_UP));
      v.put(
          "trasportoPotenzaAnno",
          obbligatorio(valori, "sigma2").add(obbligatorio(valori, "uc6s_d")));
      v.put(
          "trasportoKwh",
          obbligatorio(valori, "sigma3")
              .add(obbligatorio(valori, "uc3"))
              .add(obbligatorio(valori, "uc6p_d")));
      v.put(
          "oneriFissiMese",
          categoria.equals(RESIDENTE)
              ? BigDecimal.ZERO
              : obbligatorio(valori, "asos_dnr_f")
                  .add(obbligatorio(valori, "arim_dnr_f"))
                  .divide(new BigDecimal("12"), 8, RoundingMode.HALF_UP));
      v.put(
          "oneriKwh",
          categoria.equals(RESIDENTE)
              ? obbligatorio(valori, "asos_dr").add(obbligatorio(valori, "arim_dr"))
              : obbligatorio(valori, "asos_dnr_v").add(obbligatorio(valori, "arim_dnr_v")));
      v.put(
          "accisaKwh",
          obbligatorio(valori, categoria.equals(RESIDENTE) ? "acc_c_r_h" : "acc_c_nr"));
      v.put("aliquotaIva", obbligatorio(valori, "iva_c"));
      v.forEach(
          (key, value) -> {
            valida(key, periodo, categoria, value);
            out.add(
                new ArchivioFonti.Importato(
                    key, periodo, categoria, value, "PORTALE_OFFERTE", url, data));
          });
    }
    return out;
  }

  public List<ArchivioFonti.Importato> pun(String csv, String url) {
    var out = new ArrayList<ArchivioFonti.Importato>();
    var mesi = new HashSet<String>();
    try (var parser =
        CSVFormat.DEFAULT
            .builder()
            .setDelimiter(';')
            .setHeader()
            .setSkipHeaderRecord(true)
            .get()
            .parse(new StringReader(csv))) {
      var headers = parser.getHeaderNames();
      if (headers.size() < 2
          || !headers.get(0).equals("AnnoMese")
          || !headers.get(1).startsWith("PUN"))
        throw new IllegalArgumentException("Formato CSV indici cambiato");
      for (var row : parser) {
        String raw = row.get(0).trim();
        if (!raw.matches("[0-9]{6}"))
          throw new IllegalArgumentException("Periodo indice non valido");
        String periodo = raw.substring(0, 4) + "-" + raw.substring(4);
        if (!mesi.add(periodo)) throw new IllegalArgumentException("Indice mensile duplicato");
        var value = numero(row.get(1));
        valida("PUN_F0", periodo, INDICE, value);
        if (!YearMonth.parse(periodo).isBefore(YearMonth.now(ZoneId.of("Europe/Rome")))) continue;
        out.add(
            new ArchivioFonti.Importato(
                "PUN_F0", periodo, INDICE, value, "PORTALE_OFFERTE", url, null));
      }
    } catch (IOException e) {
      throw new IllegalArgumentException("CSV indici non leggibile");
    }
    if (out.isEmpty()) throw new IllegalArgumentException("Nessun indice storico disponibile");
    return out;
  }

  private static BigDecimal obbligatorio(Map<String, BigDecimal> v, String key) {
    var x = v.get(key);
    if (x == null || x.signum() < 0)
      throw new IllegalArgumentException("Parametro ufficiale mancante o negativo: " + key);
    return x;
  }

  private static BigDecimal numero(String s) {
    try {
      return new BigDecimal(s.trim().replace(',', '.'));
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Numero non valido nella fonte ufficiale");
    }
  }
}
