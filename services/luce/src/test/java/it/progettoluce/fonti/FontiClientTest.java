package it.progettoluce.fonti;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class FontiClientTest {
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void importaCsvRealeEConverteLeUnitaSenzaApplicareLaPcvRegolata() throws Exception {
    var client = new PortaleOfferteClient(mock(HttpFonti.class));
    var csv =
        HttpFonti.testo(
            getClass().getResourceAsStream("/fonti/parametri-20261006.csv").readAllBytes());
    var dati = client.parametri(csv, LocalDate.of(2026, 10, 6), PortaleOfferteClient.PAGINA);
    assertThat(valore(dati, "trasportoFissoMese", CatalogoFonti.RESIDENTE))
        .isEqualByComparingTo("1.92");
    assertThat(valore(dati, "trasportoPotenzaAnno", CatalogoFonti.RESIDENTE))
        .isEqualByComparingTo("23.7188");
    assertThat(valore(dati, "trasportoKwh", CatalogoFonti.RESIDENTE))
        .isEqualByComparingTo("0.01473");
    assertThat(valore(dati, "oneriKwh", CatalogoFonti.RESIDENTE)).isEqualByComparingTo("0.033153");
    assertThat(valore(dati, "oneriFissiMese", CatalogoFonti.NON_RESIDENTE))
        .isEqualByComparingTo("7.9243");
    assertThat(valore(dati, "dispacciamentoKwh", CatalogoFonti.RESIDENTE))
        .isEqualByComparingTo("0.01525091");
    assertThat(dati).allMatch(x -> x.periodo().equals("2026-10"));
    assertThat(dati.stream().filter(x -> !x.categoria().equals(CatalogoFonti.ORIGINALE)))
        .noneMatch(x -> x.codice().equals("pcv"));
    assertThatThrownBy(
            () ->
                client.parametri(
                    csv.replace("sigma1,", "parametro_rimosso,"),
                    LocalDate.of(2026, 10, 6),
                    "test"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void storicoPubblicoNonInventaFasceOPeriodi() throws Exception {
    var dati =
        new PortaleOfferteClient(mock(HttpFonti.class))
            .pun(
                HttpFonti.testo(
                    getClass().getResourceAsStream("/fonti/pun-storico.csv").readAllBytes()),
                PortaleOfferteClient.PAGINA);
    assertThat(dati).allMatch(d -> d.codice().equals("PUN_F0"));
    assertThat(
            dati.stream()
                .filter(d -> d.periodo().equals("2026-01"))
                .findFirst()
                .orElseThrow()
                .valore())
        .isEqualByComparingTo("0.132665");
  }

  @Test
  void gmeMesiCompletiOraLegaleESolareEConversioneMwh() {
    var gme = new GmeClient(mock(HttpFonti.class), json, "", "");
    for (var mese : List.of(YearMonth.of(2026, 3), YearMonth.of(2025, 10))) {
      var rows = ore(mese);
      var valori = gme.aggrega(rows, mese);
      assertThat(valori).hasSize(5);
      assertThat(valori).allMatch(v -> v.valore().compareTo(new BigDecimal("0.15")) == 0);
      rows.remove(0);
      assertThatThrownBy(() -> gme.aggrega(rows, mese)).hasMessageContaining("incompleto");
    }
  }

  @Test
  void gmeRifiutaDuplicatiETrimestriNonScambiatiPerOre() {
    var gme = new GmeClient(mock(HttpFonti.class), json, "", "");
    var mese = YearMonth.of(2026, 1);
    var rows = ore(mese);
    rows.add(rows.get(0));
    assertThatThrownBy(() -> gme.aggrega(rows, mese)).hasMessageContaining("duplicate");
    var altre = ore(mese);
    ((ObjectNode) altre.get(0)).put("Hour", 96);
    assertThatThrownBy(() -> gme.aggrega(altre, mese)).hasMessageContaining("non riconosciute");
  }

  @Test
  void fasceEscludonoFestiviEPasquetta() {
    assertThat(GmeClient.fascia(LocalDate.of(2026, 4, 6), 10)).isEqualTo("F3");
    assertThat(GmeClient.fascia(LocalDate.of(2026, 10, 5), 8)).isEqualTo("F1");
    assertThat(GmeClient.fascia(LocalDate.of(2026, 10, 3), 10)).isEqualTo("F2");
    assertThat(GmeClient.fascia(LocalDate.of(2026, 10, 5), 23)).isEqualTo("F3");
  }

  @Test
  void richiestaGmeUsaProtocolloUfficialeEArchivioJson() throws Exception {
    var http = mock(HttpFonti.class);
    var gme = new GmeClient(http, json, "utente-test", "password-test");
    when(http.post(eq(GmeClient.BASE + "/api/v1/Auth"), any(), isNull()))
        .thenReturn("{\"Success\":true,\"token\":\"jwt-test\"}".getBytes());
    var mese = YearMonth.of(2026, 1);
    var out = new java.io.ByteArrayOutputStream();
    try (var zip = new java.util.zip.ZipOutputStream(out)) {
      zip.putNextEntry(new java.util.zip.ZipEntry("prezzi.json"));
      zip.write(json.writeValueAsBytes(ore(mese)));
      zip.closeEntry();
    }
    var envelope =
        json.writeValueAsBytes(
            Map.of(
                "FormatType",
                ".json.zip",
                "ContentResponse",
                Base64.getEncoder().encodeToString(out.toByteArray())));
    when(http.post(eq(GmeClient.BASE + "/api/v1/RequestData"), any(), eq("jwt-test")))
        .thenReturn(envelope);
    assertThat(gme.scarica(mese)).hasSize(5);
    var body = org.mockito.ArgumentCaptor.forClass(byte[].class);
    verify(http).post(eq(GmeClient.BASE + "/api/v1/RequestData"), body.capture(), eq("jwt-test"));
    var request = json.readTree(body.getValue());
    assertThat(request.path("Attributes").path("GranularityType").asText()).isEqualTo("PT60");
    assertThat(request.path("IntervalEnd").asInt()).isEqualTo(20260131);
  }

  @Test
  void httpRifiutaFontiArbitrarie() {
    assertThatThrownBy(() -> new HttpFonti().get("http://localhost/test"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private ArrayNode ore(YearMonth m) {
    var a = json.createArrayNode();
    var zone = ZoneId.of("Europe/Rome");
    for (var date = m.atDay(1); !date.isAfter(m.atEndOfMonth()); date = date.plusDays(1)) {
      int n =
          (int)
              Duration.between(date.atStartOfDay(zone), date.plusDays(1).atStartOfDay(zone))
                  .toHours();
      for (int h = 1; h <= n; h++)
        a.addObject()
            .put("FlowDate", date.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE))
            .put("Hour", h)
            .put("Zone", "PUN")
            .put("Market", "MGP")
            .put("Price", "150");
    }
    return a;
  }

  private BigDecimal valore(List<ArchivioFonti.Importato> d, String c, String cat) {
    return d.stream()
        .filter(x -> x.codice().equals(c) && x.categoria().equals(cat))
        .findFirst()
        .orElseThrow()
        .valore();
  }
}
