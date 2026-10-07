package it.progettoluce.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BusinessWorkspaceTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  JsonNode send(MockHttpServletRequestBuilder request, String body, int status) throws Exception {
    return json.readTree(
        mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().is(status))
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  @Test
  void flussoCompletoBusinessSenzaChiaveQuotePotenzaSnapshotEPdf() throws Exception {
    String params =
        """
        {"nomeProfilo":"Business test","fonte":"Fixture sintetica", "coefficientePerdite":"0",
         "arrotondaPerdite":true,"dispacciamentoKwh":"0","trasportoFissoMese":"0",
         "trasportoPotenzaAnno":"0","trasportoKwh":"0","oneriFissiMese":"0",
         "oneriKwh":"0","accisaKwh":"0","oneriPotenzaMese":"2","oneriSuPerdite":false}
        """;
    var data = (ObjectNode) json.readTree(params);
    var existing = mvc.perform(get("/api/parametri")).andReturn().getResponse();
    if (existing.getStatus() == 200)
      data.set("versione", json.readTree(existing.getContentAsString()).get("versione"));
    var saved = send(put("/api/parametri"), data.toString(), 200);
    assertThat(saved.path("oneriPotenzaMese").asText()).isEqualTo("2");
    var offer =
        send(
            post("/api/offerte"),
            """
{"nomeFornitore":"Demo","nomeOfferta":"Business test","tipoOfferta":"PREZZO_FISSO",
 "tipoTariffa":"MONORARIA","prezzi":{"f0":"0.1"},"spread":null,"pcvAnnuo":"120","attiva":true,"note":"test"}
""",
            201);
    var bill =
        send(
            post("/api/bollette"),
            """
{"cliente":"Impresa test","partitaIva":"12345678901","pod":"IT001E00000000","fornitore":"Demo",
 "potenzaKw":"17.8","totaleFatturato":"200","aliquotaIva":"0.22",
 "altrePartiteImponibili":"0","altrePartiteEsenti":"0",
 "mesi":[{"mese":"2026-01","f1":"0","f2":"0","f3":"0","pun":null,"quoteFisse":0},
         {"mese":"2026-02","f1":"200","f2":"200","f3":"200","pun":null,"quoteFisse":1}]}
""",
            201);
    assertThat(bill.path("dati").path("partitaIva").asText()).isEqualTo("12345678901");
    String request =
        "{\"bollettaId\":" + bill.path("id") + ",\"offertaId\":" + offer.path("id") + "}";
    var result = send(post("/api/confronti"), request, 201);
    assertThat(result.path("dati").path("risultato").path("totale").asText()).isEqualTo("128.83");
    assertThat(result.path("dati").path("risultato").path("stimaRisparmioAnnuale").asText())
        .isEqualTo("427.02");
    var update = (ObjectNode) saved;
    update.put("oneriPotenzaMese", "4");
    send(put("/api/parametri"), update.toString(), 200);
    var stored =
        json.readTree(
            mvc.perform(get("/api/confronti/" + result.path("id")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(stored.path("dati").path("risultato").path("totale").asText()).isEqualTo("128.83");
    byte[] pdf =
        mvc.perform(get("/api/confronti/" + result.path("id") + "/pdf"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII))
        .isEqualTo("%PDF-");
    var withLoss = (ObjectNode) send(get("/api/parametri"), "", 200);
    withLoss.put("coefficientePerdite", "0.10").put("oneriKwh", "0.01").put("oneriSuPerdite", true);
    send(put("/api/parametri"), withLoss.toString(), 200);
    var recalculated = send(post("/api/confronti"), request, 201);
    assertThat(recalculated.path("dati").path("risultato").path("totale").asText())
        .isEqualTo("187.64");
    mvc.perform(get("/api/impostazioni-pdf")).andExpect(status().isOk());
    mvc.perform(get("/api/fonti")).andExpect(status().isOk());
    var official = (ObjectNode) json.readTree(request);
    official
        .put("usaFontiUfficiali", true)
        .put("categoria", "DOMESTICO_RESIDENTE")
        .put("confermaStandard", true);
    mvc.perform(
            post("/api/confronti")
                .contentType(MediaType.APPLICATION_JSON)
                .content(official.toString()))
        .andExpect(status().isBadRequest());
  }
}
