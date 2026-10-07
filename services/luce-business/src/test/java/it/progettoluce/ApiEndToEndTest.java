package it.progettoluce;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.progettoluce.bollette.*;
import it.progettoluce.offerte.*;
import it.progettoluce.parametri.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "domestico"})
class ApiEndToEndTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired OffertaRepository offerte;
  @Autowired EntityManager em;
  @Autowired PlatformTransactionManager tm;

  @BeforeEach
  void pulisci() throws Exception {
    var opzioni =
        new it.progettoluce.confronti.PdfPersonalizzazione(
            it.progettoluce.confronti.PdfPersonalizzazione.Stile.CLASSICO,
            "#194D3D",
            null,
            new it.progettoluce.confronti.PdfPersonalizzazione.Consulente(
                "", "", "", "", "", true));
    jdbc.update(
        "UPDATE impostazioni_pdf SET versione=0, configurazione=? WHERE id=1",
        json.writeValueAsString(opzioni));
    for (String tabella :
        List.of(
            "revisioni_fonti",
            "dati_ufficiali",
            "sincronizzazioni_fonti",
            "confronti",
            "voci_corrispettivo",
            "offerte",
            "mesi_bolletta",
            "bollette",
            "parametri_gestore")) jdbc.update("DELETE FROM " + tabella);
  }

  @ParameterizedTest
  @CsvSource({
    "PREZZO_FISSO,MONORARIA,2",
    "PREZZO_FISSO,BIORARIA,3",
    "PREZZO_FISSO,TRIORARIA,4",
    "INDICIZZATA_PUN,MONORARIA,2",
    "INDICIZZATA_PUN,BIORARIA,3",
    "INDICIZZATA_PUN,TRIORARIA,4"
  })
  void creaRicaricaECalcolaConLeVociPersistite(TipoOfferta tipo, TipoTariffa tariffa, int count)
      throws Exception {
    long id = postJson("/api/offerte", request(tipo, tariffa, "0.1", null)).get("id").asLong();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM voci_corrispettivo WHERE offerta_id=?", Integer.class, id))
        .isEqualTo(count);
    new TransactionTemplate(tm)
        .executeWithoutResult(
            status -> {
              em.clear();
              var salvata = offerte.findById(id).orElseThrow();
              assertThat(salvata.getVoci()).hasSize(count);
              salvata.getVoci().forEach(v -> assertThat(v.getOffertaId()).isEqualTo(id));
            });
    var recuperata = getJson("/api/offerte/" + id);
    assertThat(recuperata.get("pcvAnnuo").asText()).isEqualTo("120.00000000");
    assertThat(recuperata.get("voci").size()).isEqualTo(count);
    salvaParametri();
    long bid = postJson("/api/bollette", bolletta()).get("id").asLong();
    var confronto =
        postJson("/api/confronti", java.util.Map.of("bollettaId", bid, "offertaId", id));
    var risultato = confronto.path("dati").path("risultato");
    // 600 kWh × 0.1 + 120/12 PCV = 70 taxable, + 10% VAT = 77.
    assertThat(new BigDecimal(risultato.get("totale").asText())).isEqualByComparingTo("77.00");
    assertThat(new BigDecimal(risultato.get("risparmioPeriodo").asText()))
        .isEqualByComparingTo("123.00");
    assertThat(risultato.get("righe").size()).isGreaterThanOrEqualTo(count);
    jdbc.update("DELETE FROM voci_corrispettivo WHERE offerta_id=?", id);
    mvc.perform(
            post("/api/confronti")
                .contentType("application/json")
                .content(
                    json.writeValueAsString(java.util.Map.of("bollettaId", bid, "offertaId", id))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("L'offerta non ha voci di calcolo"));
  }

  @Test
  void aggiornaTariffaEliminaOrfaniEProteggeVersione() throws Exception {
    var creata =
        postJson(
            "/api/offerte", request(TipoOfferta.PREZZO_FISSO, TipoTariffa.MONORARIA, "0.1", null));
    long id = creata.get("id").asLong();
    Long oldVoce =
        jdbc.queryForObject(
            "SELECT MIN(id) FROM voci_corrispettivo WHERE offerta_id=?", Long.class, id);
    var aggiornamento =
        request(
            TipoOfferta.INDICIZZATA_PUN,
            TipoTariffa.TRIORARIA,
            "0.2",
            creata.get("versione").asLong());
    mvc.perform(
            put("/api/offerte/" + id)
                .contentType("application/json")
                .content(json.writeValueAsString(aggiornamento)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.voci.length()").value(4))
        .andExpect(jsonPath("$.prezzi").doesNotExist());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM voci_corrispettivo WHERE offerta_id=?", Integer.class, id))
        .isEqualTo(4);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM voci_corrispettivo WHERE id=?", Integer.class, oldVoce))
        .isZero();
    mvc.perform(
            put("/api/offerte/" + id)
                .contentType("application/json")
                .content(json.writeValueAsString(aggiornamento)))
        .andExpect(status().isConflict());
    mvc.perform(delete("/api/offerte/" + id)).andExpect(status().isNoContent());
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM voci_corrispettivo", Integer.class))
        .isZero();
  }

  @Test
  void storicoRestaInvariatoDopoModificaEdEliminazione() throws Exception {
    salvaParametri();
    var creata =
        postJson(
            "/api/offerte", request(TipoOfferta.PREZZO_FISSO, TipoTariffa.MONORARIA, "0.1", null));
    long oid = creata.get("id").asLong();
    long bid = postJson("/api/bollette", bolletta()).get("id").asLong();
    long cid =
        postJson("/api/confronti", java.util.Map.of("bollettaId", bid, "offertaId", oid))
            .get("id")
            .asLong();
    mvc.perform(
            put("/api/offerte/" + oid)
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        request(
                            TipoOfferta.PREZZO_FISSO,
                            TipoTariffa.MONORARIA,
                            "0.2",
                            creata.get("versione").asLong()))))
        .andExpect(status().isOk());
    var nuovo = postJson("/api/confronti", java.util.Map.of("bollettaId", bid, "offertaId", oid));
    assertThat(new BigDecimal(nuovo.path("dati").path("risultato").path("totale").asText()))
        .isEqualByComparingTo("143");
    mvc.perform(delete("/api/offerte/" + oid)).andExpect(status().isNoContent());
    mvc.perform(delete("/api/bollette/" + bid)).andExpect(status().isNoContent());
    assertThat(
            new BigDecimal(
                getJson("/api/confronti/" + cid)
                    .path("dati")
                    .path("risultato")
                    .path("totale")
                    .asText()))
        .isEqualByComparingTo("77");
  }

  @Test
  void aggiornaMesiSenzaDuplicareRecord() throws Exception {
    var creata = postJson("/api/bollette", bolletta());
    long id = creata.get("id").asLong();
    var r = bolletta();
    var update =
        new BollettaRequest(
            r.cliente(),
            r.pod(),
            r.fornitore(),
            r.potenzaKw(),
            r.totaleFatturato(),
            r.aliquotaIva(),
            r.altrePartiteImponibili(),
            r.altrePartiteEsenti(),
            r.mesi(),
            creata.path("dati").get("versione").asLong());
    mvc.perform(
            put("/api/bollette/" + id)
                .contentType("application/json")
                .content(json.writeValueAsString(update)))
        .andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM mesi_bolletta WHERE bolletta_id=?", Integer.class, id))
        .isEqualTo(1);
  }

  @Test
  void validaRichiesteENotFound() throws Exception {
    mvc.perform(post("/api/offerte").contentType("application/json").content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.nomeOfferta").exists());
    mvc.perform(
            post("/api/offerte")
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        request(TipoOfferta.PREZZO_FISSO, TipoTariffa.MONORARIA, "-0.1", null))))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/offerte/999999")).andExpect(status().isNotFound());
    mvc.perform(delete("/api/offerte/999999")).andExpect(status().isNotFound());
    mvc.perform(get("/api/bollette/999999")).andExpect(status().isNotFound());
    mvc.perform(get("/api/confronti/999999")).andExpect(status().isNotFound());
    mvc.perform(get("/api/parametri")).andExpect(status().isNotFound());
  }

  @Test
  void precisioneOltreOttoDecimaliVieneRifiutata() throws Exception {
    mvc.perform(
            post("/api/offerte")
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        request(
                            TipoOfferta.PREZZO_FISSO, TipoTariffa.MONORARIA, "0.123456789", null))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rifiutaMesiNonConsecutivi() throws Exception {
    var r = bolletta();
    var mesi = List.of(r.mesi().get(0), new MeseRequest("2026-03", b("0"), b("0"), b("0"), null));
    var invalida =
        new BollettaRequest(
            r.cliente(),
            r.pod(),
            r.fornitore(),
            r.potenzaKw(),
            r.totaleFatturato(),
            r.aliquotaIva(),
            r.altrePartiteImponibili(),
            r.altrePartiteEsenti(),
            mesi,
            null);
    mvc.perform(
            post("/api/bollette")
                .contentType("application/json")
                .content(json.writeValueAsString(invalida)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void controlloVersioneParametri() throws Exception {
    salvaParametri();
    mvc.perform(
            put("/api/parametri")
                .contentType("application/json")
                .content(json.writeValueAsString(parametri())))
        .andExpect(status().isConflict());
  }

  private void salvaParametri() throws Exception {
    mvc.perform(
            put("/api/parametri")
                .contentType("application/json")
                .content(json.writeValueAsString(parametri())))
        .andExpect(status().isOk());
  }

  @Test
  void scaricaPdfDaSnapshotAncheDopoEliminazioneDelleSorgenti() throws Exception {
    salvaParametri();
    long oid =
        postJson(
                "/api/offerte",
                request(TipoOfferta.PREZZO_FISSO, TipoTariffa.MONORARIA, "0.1", null))
            .get("id")
            .asLong();
    long bid = postJson("/api/bollette", bolletta()).get("id").asLong();
    long cid =
        postJson("/api/confronti", java.util.Map.of("bollettaId", bid, "offertaId", oid))
            .get("id")
            .asLong();
    mvc.perform(delete("/api/offerte/" + oid)).andExpect(status().isNoContent());
    mvc.perform(delete("/api/bollette/" + bid)).andExpect(status().isNoContent());
    jdbc.update("DELETE FROM parametri_gestore");
    byte[] bytes =
        mvc.perform(get("/api/confronti/" + cid + "/pdf"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/pdf"))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(
                header()
                    .string(
                        "Content-Disposition",
                        "attachment; filename=\"confronto-" + cid + ".pdf\""))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    try (var pdf = org.apache.pdfbox.Loader.loadPDF(bytes)) {
      assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(pdf))
          .contains("77,00 €", "123,00 €", "Cliente test");
    }
    byte[] custom =
        mvc.perform(
                post("/api/confronti/" + cid + "/pdf")
                    .contentType("application/json")
                    .content(
                        "{\"stile\":\"SINTESI\",\"colore\":\"#1E3A8A\",\"consulente\":{\"nome\":\"Studio"
                            + " Test\",\"dimostrativo\":true}}"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/pdf"))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    try (var pdf = org.apache.pdfbox.Loader.loadPDF(custom)) {
      assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(pdf))
          .contains("Studio Test", "77,00 €", "123,00 €");
    }
    var preview =
        mvc.perform(
                post("/api/confronti/" + cid + "/pdf/anteprima")
                    .contentType("application/json")
                    .content("{\"stile\":\"SINTESI\"}"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/json"))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    var previewJson = json.readTree(preview);
    assertThat(previewJson.get("pagine").size()).isGreaterThan(0);
    try (var pdf =
        org.apache.pdfbox.Loader.loadPDF(
            java.util.Base64.getDecoder().decode(previewJson.get("pdfBase64").asText()))) {
      assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(pdf))
          .contains("77,00 €", "123,00 €");
      assertThat(pdf.getNumberOfPages()).isEqualTo(previewJson.get("pagine").size());
    }
    for (String invalid :
        java.util.List.of(
            "{\"colore\":\"rosso\"}",
            "{\"stile\":\"IGNOTO\"}",
            "{\"logo\":\"data:image/png;base64,invalid\"}",
            "{\"consulente\":{\"nome\":\"" + "x".repeat(101) + "\"}}"))
      mvc.perform(
              post("/api/confronti/" + cid + "/pdf")
                  .contentType("application/json")
                  .content(invalid))
          .andExpect(status().isBadRequest());
    mvc.perform(post("/api/confronti/999999/pdf").contentType("application/json").content("{}"))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/confronti/999999/pdf")).andExpect(status().isNotFound());
  }

  @Test
  void salvaImpostazioniPdfELeApplicaAutomaticamenteAlDownload() throws Exception {
    var config =
        json.readTree(
            mvc.perform(get("/api/impostazioni-pdf"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    String request =
        "{\"versione\":"
            + config.get("versione")
            + ",\"opzioni\":{\"stile\":\"SINTESI\",\"colore\":\"#6b21a8\",\"consulente\":{\"nome\":\"Studio"
            + " Persistente\",\"dimostrativo\":true}}}";
    mvc.perform(put("/api/impostazioni-pdf").contentType("application/json").content(request))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.opzioni.colore").value("#6B21A8"))
        .andExpect(jsonPath("$.versione").value(1));
    mvc.perform(get("/api/impostazioni-pdf"))
        .andExpect(jsonPath("$.opzioni.consulente.nome").value("Studio Persistente"));
    mvc.perform(put("/api/impostazioni-pdf").contentType("application/json").content(request))
        .andExpect(status().isConflict());
    assertThat(
            jdbc.queryForObject(
                "SELECT configurazione FROM impostazioni_pdf WHERE id=1", String.class))
        .contains("Studio Persistente", "#6B21A8");
    salvaParametri();
    long oid =
        postJson(
                "/api/offerte",
                request(TipoOfferta.PREZZO_FISSO, TipoTariffa.MONORARIA, "0.1", null))
            .get("id")
            .asLong();
    long bid = postJson("/api/bollette", bolletta()).get("id").asLong();
    long cid =
        postJson("/api/confronti", java.util.Map.of("bollettaId", bid, "offertaId", oid))
            .get("id")
            .asLong();
    byte[] bytes =
        mvc.perform(get("/api/confronti/" + cid + "/pdf"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    try (var pdf = org.apache.pdfbox.Loader.loadPDF(bytes)) {
      assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(pdf))
          .contains("Studio Persistente", "Quanto puoi risparmiare", "77,00 €", "123,00 €");
    }
  }

  @Test
  void anteprimaImpostazioniFunzionaSenzaConfrontiENonSalvaLaBozza() throws Exception {
    var initial =
        mvc.perform(get("/api/impostazioni-pdf")).andReturn().getResponse().getContentAsString();
    mvc.perform(
            post("/api/impostazioni-pdf/anteprima")
                .contentType("application/json")
                .content("{\"stile\":\"EDITORIALE\",\"colore\":\"#1E3A8A\"}"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.pagine[0]").exists());
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM confronti", Integer.class)).isZero();
    assertThat(
            mvc.perform(get("/api/impostazioni-pdf"))
                .andReturn()
                .getResponse()
                .getContentAsString())
        .isEqualTo(initial);
    for (String invalid :
        List.of(
            "{\"opzioni\":{},\"versione\":-1}",
            "{\"opzioni\":{\"colore\":\"rosso\"},\"versione\":0}",
            "{\"opzioni\":{\"logo\":\"data:image/png;base64,invalid\"},\"versione\":0}"))
      mvc.perform(put("/api/impostazioni-pdf").contentType("application/json").content(invalid))
          .andExpect(status().isBadRequest());
    assertThat(
            mvc.perform(get("/api/impostazioni-pdf"))
                .andReturn()
                .getResponse()
                .getContentAsString())
        .isEqualTo(initial);
  }

  @Test
  void confrontoMensileUfficialeSalvaFontiEPdfImmutabili() throws Exception {
    salvaParametri();
    long oid =
        postJson(
                "/api/offerte",
                request(TipoOfferta.INDICIZZATA_PUN, TipoTariffa.MONORARIA, "0.1", null))
            .path("id")
            .asLong();
    long bid = postJson("/api/bollette", bolletta()).path("id").asLong();
    for (String codice : it.progettoluce.fonti.CatalogoFonti.UNITA.keySet()) {
      String valore = codice.equals("aliquotaIva") ? "0.1" : codice.equals("PUN_F0") ? "0.1" : "0";
      mvc.perform(
              put("/api/fonti/dati")
                  .contentType("application/json")
                  .content(
                      json.writeValueAsString(
                          java.util.Map.of(
                              "codice",
                              codice,
                              "periodo",
                              "2026-01",
                              "categoria",
                              codice.startsWith("PUN_") ? "INDICE" : "DOMESTICO_RESIDENTE",
                              "valore",
                              valore,
                              "motivo",
                              "Fixture verificata"))))
          .andExpect(status().isOk());
    }
    var payload =
        java.util.Map.of(
            "bollettaId",
            bid,
            "offertaId",
            oid,
            "usaFontiUfficiali",
            true,
            "categoria",
            "DOMESTICO_RESIDENTE",
            "confermaStandard",
            true);
    var confronto = postJson("/api/confronti", payload);
    assertThat(confronto.path("dati").path("parametriMensili").get(0).path("fonti").size())
        .isEqualTo(14);
    assertThat(new BigDecimal(confronto.path("dati").path("risultato").path("totale").asText()))
        .isEqualByComparingTo("83.6");
    assertThat(
            getJson("/api/bollette/" + bid)
                .path("dati")
                .path("mesi")
                .get(0)
                .path("pun")
                .path("f0")
                .asText())
        .isEqualTo("0.09000000");
    var indice = getJson("/api/fonti/dati?periodo=2026-01&categoria=INDICE").get(0);
    mvc.perform(
            put("/api/fonti/dati")
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        java.util.Map.of(
                            "codice",
                            indice.path("codice").asText(),
                            "periodo",
                            "2026-01",
                            "categoria",
                            "INDICE",
                            "valore",
                            "0.2",
                            "versione",
                            indice.path("versione").asLong(),
                            "motivo",
                            "Nuova verifica"))))
        .andExpect(status().isOk());
    assertThat(getJson("/api/confronti/" + confronto.path("id").asLong()).path("dati"))
        .isEqualTo(confronto.path("dati"));
    var bytes =
        mvc.perform(get("/api/confronti/" + confronto.path("id").asLong() + "/pdf"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    try (var pdf = org.apache.pdfbox.Loader.loadPDF(bytes)) {
      assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(pdf))
          .contains("Fonti e parametri mensili salvati", "correzione manuale", "83,60");
    }
    mvc.perform(get("/api/fonti/dati?periodo=nonvalido&categoria=INDICE"))
        .andExpect(status().isBadRequest());
  }

  private ParametriRequest parametri() {
    return new ParametriRequest(
        "Test",
        "Valori sintetici",
        b("0"),
        false,
        b("0"),
        b("0"),
        b("0"),
        b("0"),
        b("0"),
        b("0"),
        b("0"),
        null);
  }

  private BollettaRequest bolletta() {
    var pun = new Prezzi(b("0.09"), b("0.09"), b("0.09"), b("0.09"), b("0.09"));
    return new BollettaRequest(
        "Cliente test",
        "IT001E12345678",
        "Attuale",
        b("3"),
        b("200"),
        b("0.1"),
        b("0"),
        b("0"),
        List.of(new MeseRequest("2026-01", b("100"), b("200"), b("300"), pun)),
        null);
  }

  private OffertaRequest request(
      TipoOfferta tipo, TipoTariffa tariffa, String prezzo, Long versione) {
    var valori = new Prezzi(b(prezzo), b(prezzo), b(prezzo), b(prezzo), b(prezzo));
    var spread = new Prezzi(b("0.01"), b("0.01"), b("0.01"), b("0.01"), b("0.01"));
    return new OffertaRequest(
        "Test", "Offerta", tipo, tariffa, valori, spread, b("120"), true, "", versione);
  }

  private JsonNode postJson(String path, Object body) throws Exception {
    return json.readTree(
        mvc.perform(
                post(path).contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode getJson(String path) throws Exception {
    return json.readTree(
        mvc.perform(get(path))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private static BigDecimal b(String value) {
    return new BigDecimal(value);
  }
}
