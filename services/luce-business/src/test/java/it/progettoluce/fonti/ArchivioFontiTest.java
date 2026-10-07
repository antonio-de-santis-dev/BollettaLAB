package it.progettoluce.fonti;

import static org.assertj.core.api.Assertions.*;

import it.progettoluce.bollette.*;
import it.progettoluce.offerte.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles({"test", "domestico"})
@Transactional
class ArchivioFontiTest {
  @Autowired ArchivioFonti archivio;
  @Autowired ParametriUfficiali resolver;
  @Autowired it.progettoluce.calcolo.MotoreCalcolo motore;
  final String cat = CatalogoFonti.RESIDENTE;

  private ArchivioFonti.Importato dato(String c, String m, String v) {
    return new ArchivioFonti.Importato(
        c,
        m,
        cat,
        new BigDecimal(v),
        "PORTALE_OFFERTE",
        PortaleOfferteClient.PAGINA,
        LocalDate.of(2026, 10, 6));
  }

  @Test
  void reimportazioneConservaOverrideStoricoEVersione() {
    archivio.importa(List.of(dato("accisaKwh", "2026-10", "0.0227")));
    var iniziale = archivio.lista("2026-10", cat).get(0);
    var manuale =
        archivio.manuale(
            "accisaKwh",
            "2026-10",
            cat,
            new BigDecimal("0.01"),
            iniziale.versione(),
            "Verifica bolletta");
    archivio.importa(List.of(dato("accisaKwh", "2026-10", "0.023")));
    var aggiornato = archivio.lista("2026-10", cat).get(0);
    assertThat(aggiornato.valore()).isEqualByComparingTo("0.01");
    assertThat(aggiornato.valoreUfficiale()).isEqualByComparingTo("0.023");
    assertThat(archivio.revisioni(aggiornato.id())).hasSize(3);
    assertThatThrownBy(
            () -> archivio.ripristina(aggiornato.id(), manuale.versione(), "Versione vecchia"))
        .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
    assertThat(
            archivio
                .ripristina(aggiornato.id(), aggiornato.versione(), "Usa dato ufficiale")
                .valore())
        .isEqualByComparingTo("0.023");
  }

  @Test
  void datiGmePrevalgonoSulFallbackPubblico() {
    archivio.importa(
        List.of(
            new ArchivioFonti.Importato(
                "PUN_F0",
                "2026-01",
                CatalogoFonti.INDICE,
                new BigDecimal("0.1"),
                "GME",
                GmeClient.BASE,
                null)));
    archivio.importa(
        List.of(
            new ArchivioFonti.Importato(
                "PUN_F0",
                "2026-01",
                CatalogoFonti.INDICE,
                new BigDecimal("0.2"),
                "PORTALE_OFFERTE",
                PortaleOfferteClient.PAGINA,
                null)));
    assertThat(archivio.lista("2026-01", CatalogoFonti.INDICE).get(0).valore())
        .isEqualByComparingTo("0.1");
  }

  @Test
  void usaTariffeMensiliSenzaModificareBollettaOriginale() {
    for (String m : List.of("2026-01", "2026-02"))
      for (String c : CatalogoFonti.UNITA.keySet())
        if (!c.startsWith("PUN_"))
          archivio.importa(
              List.of(
                  dato(
                      c,
                      m,
                      c.equals("aliquotaIva")
                          ? "0.1"
                          : c.equals("trasportoFissoMese")
                              ? (m.endsWith("01") ? "10" : "20")
                              : "0")));
    var b = bolletta(new BigDecimal("4"));
    var o = offerta();
    var preparato = resolver.prepara(b, o, cat);
    var profili = new HashMap<String, it.progettoluce.parametri.ParametriRequest>();
    preparato.mesi().forEach(m -> profili.put(m.mese(), m.parametri()));
    assertThat(motore.calcola(preparato.bolletta(), o, profili).totale())
        .isEqualByComparingTo("55");
    assertThat(b.aliquotaIva()).isEqualByComparingTo("0.22");
    assertThat(preparato.bolletta().getAliquotaIva()).isEqualByComparingTo("0.1");
    assertThatThrownBy(() -> resolver.prepara(bolletta(new BigDecimal("3")), o, cat))
        .hasMessageContaining("scaglioni");
  }

  @Test
  void periodoMancanteNonUsaTariffeDiOttobre() {
    archivio.importa(List.of(dato("accisaKwh", "2026-10", "0.0227")));
    assertThatThrownBy(() -> resolver.prepara(bolletta(new BigDecimal("4")), offerta(), cat))
        .hasMessageContaining("2026-01");
  }

  @Test
  void fallimentoSyncNonCancellaDatiERegistraEsito() {
    archivio.importa(List.of(dato("accisaKwh", "2026-10", "0.0227")));
    var http = org.mockito.Mockito.mock(HttpFonti.class);
    org.mockito.Mockito.when(http.get(org.mockito.ArgumentMatchers.anyString()))
        .thenThrow(new IllegalArgumentException("Rete"));
    var sync =
        new SincronizzazioneFonti(
            archivio,
            new PortaleOfferteClient(http),
            new GmeClient(http, new com.fasterxml.jackson.databind.ObjectMapper(), "", ""),
            false);
    sync.sincronizza(null);
    assertThat(archivio.esiti().get(0).successo()).isFalse();
    assertThat(archivio.lista("2026-10", cat)).hasSize(1);
  }

  private BollettaRequest bolletta(BigDecimal kw) {
    return new BollettaRequest(
        "Test",
        "ITTEST",
        "Test",
        kw,
        new BigDecimal("200"),
        new BigDecimal("0.22"),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        List.of(
            new MeseRequest(
                "2026-01", new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO, null),
            new MeseRequest(
                "2026-02", new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO, null)),
        null);
  }

  private Offerta offerta() {
    var o = new Offerta();
    var request =
        new OffertaRequest(
            "Test",
            "Test",
            TipoOfferta.PREZZO_FISSO,
            TipoTariffa.MONORARIA,
            new Prezzi(new BigDecimal("0.1"), null, null, null, null),
            null,
            BigDecimal.ZERO,
            true,
            "",
            null);
    o.aggiorna(request, new OffertaVociFactory().crea(request));
    return o;
  }
}
