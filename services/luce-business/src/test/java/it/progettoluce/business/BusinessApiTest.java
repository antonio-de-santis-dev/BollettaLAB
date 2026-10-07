package it.progettoluce.business;

import static it.progettoluce.business.BusinessEngineTest.*;
import static it.progettoluce.business.BusinessModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BusinessApiTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  String payload(Object o) throws Exception {
    return json.writeValueAsString(o);
  }

  ProfiloInput tariffa() {
    return profilo(voce("a", Categoria.ENERGIA, Base.KWH_FASCIA, Fascia.F0, "0.1", "0.22", false))
        .dati();
  }

  JsonNode crea() throws Exception {
    return json.readTree(
        mvc.perform(
                post("/api/business/profili")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload(tariffa())))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  @Test
  void modificaTariffeSenzaChiaveEInputValidato() throws Exception {
    mvc.perform(
            post("/api/business/profili")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload(tariffa())))
        .andExpect(status().isCreated());
    mvc.perform(post("/api/business/profili").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/business/simulazioni?pagina=-1")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/business/simulazioni?dimensione=101")).andExpect(status().isBadRequest());
  }

  @Test
  void versioniRevisioniSnapshotEPdfImmutabili() throws Exception {
    var p = crea();
    long id = p.path("id").asLong();
    long versione = p.path("versione").asLong();
    var m = new MeseInput("2026-01", id, n("600"), n("0"), n("0"), 1, null);
    var input = input(List.of(m), List.of(), "200");
    var sim =
        json.readTree(
            mvc.perform(
                    post("/api/business/simulazioni")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(input)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(sim.path("dati").path("risultato").path("totale").asText()).isEqualTo("73.20");
    var old = json.treeToValue(p.path("dati"), ProfiloInput.class);
    var updated =
        new ProfiloInput(
            old.nome(),
            old.fonte(),
            old.dal(),
            old.al(),
            old.verificato(),
            old.notaVerifica(),
            old.perdite(),
            old.arrotondaPerdite(),
            List.of(voce("a", Categoria.ENERGIA, Base.KWH_FASCIA, Fascia.F0, "0.2", "0.22", false)),
            versione);
    mvc.perform(
            put("/api/business/profili/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload(updated)))
        .andExpect(status().isOk());
    mvc.perform(
            put("/api/business/profili/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload(updated)))
        .andExpect(status().isConflict());
    var history =
        json.readTree(
            mvc.perform(get("/api/business/profili/" + id + "/revisioni"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(history.size()).isEqualTo(2);
    long sid = sim.path("id").asLong();
    var saved =
        json.readTree(
            mvc.perform(get("/api/business/simulazioni/" + sid))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(saved).isEqualTo(sim);
    var bytes =
        mvc.perform(get("/api/business/simulazioni/" + sid + "/pdf"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    try (var pdf = org.apache.pdfbox.Loader.loadPDF(bytes)) {
      String text = new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);
      assertThat(text).contains("73,20", "versione 0", "Impresa di test", "0.1");
    }
    var list =
        json.readTree(
            mvc.perform(get("/api/business/simulazioni?dimensione=1"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(list.path("contenuto").get(0).has("dati")).isFalse();
    assertThat(list.path("contenuto").size()).isEqualTo(1);
  }

  @Test
  void mancaFasciaENotaVerificaEQuantitaNegativeRifiutate() throws Exception {
    var r = tariffa();
    var invalid =
        new ProfiloInput(
            r.nome(), r.fonte(), r.dal(), r.al(), true, "", r.perdite(), true, r.voci(), null);
    mvc.perform(
            post("/api/business/profili")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload(invalid)))
        .andExpect(status().isBadRequest());
    var p = crea();
    var bad =
        input(
            List.of(
                new MeseInput("2026-01", p.path("id").asLong(), n("-1"), n("0"), n("0"), 1, null)),
            List.of(),
            "200");
    mvc.perform(
            post("/api/business/simulazioni")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload(bad)))
        .andExpect(status().isBadRequest());
  }
}
