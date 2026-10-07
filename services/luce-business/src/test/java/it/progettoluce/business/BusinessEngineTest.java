package it.progettoluce.business;

import static it.progettoluce.business.BusinessModels.*;
import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class BusinessEngineTest {
  final BusinessEngine engine = new BusinessEngine();

  static BigDecimal n(String n) {
    return new BigDecimal(n);
  }

  static Voce voce(
      String code, Categoria cat, Base base, Fascia f, String price, String iva, boolean pun) {
    return new Voce(code, code, cat, base, f, n(price), pun, n(iva));
  }

  static Profilo profilo(Voce... v) {
    return new Profilo(
        1L,
        0L,
        new ProfiloInput(
            "Fixture sintetica",
            "Test automatico",
            "2026-01",
            "2026-12",
            true,
            "Verificato per il solo test",
            n("0.10"),
            true,
            List.of(v),
            null));
  }

  static MeseInput mese(String name, String f1, String f2, String f3, int quote) {
    return new MeseInput(name, 1L, n(f1), n(f2), n(f3), quote, null);
  }

  static SimulazioneInput input(List<MeseInput> mesi, List<Partita> altre, String precedente) {
    return new SimulazioneInput(
        "Impresa di test",
        "IT001E00000000",
        "",
        LocalDate.of(2026, 2, 28),
        n("17.8"),
        n(precedente),
        true,
        mesi,
        altre);
  }

  Risultato calcola(Profilo p, List<MeseInput> m, List<Partita> a, String previous) {
    return engine.calcola(input(m, a, previous), Map.of(1L, p));
  }

  @Test
  void riconciliaAggregatiDelDocumentoSenzaInventareGliOttoEuroMancanti() {
    var p =
        profilo(
            voce("energia", Categoria.ENERGIA, Base.QUOTA_MESE, null, "988.20", "0.22", false),
            voce("rete", Categoria.TRASPORTO, Base.QUOTA_MESE, null, "382.20", "0.22", false),
            voce("imposte", Categoria.IMPOSTE, Base.QUOTA_MESE, null, "63.75", "0.22", false));
    var r =
        calcola(
            p,
            List.of(mese("2026-01", "0", "0", "0", 0), mese("2026-02", "1200", "1425", "2475", 1)),
            List.of(),
            "1811.79");
    assertThat(r.imponibile()).isEqualByComparingTo("1434.15");
    assertThat(r.totaleIva()).isEqualByComparingTo("315.51");
    assertThat(r.totale()).isEqualByComparingTo("1749.66");
    assertThat(r.risparmioPeriodo()).isEqualByComparingTo("62.13");
    assertThat(r.risparmioAnnualizzato()).isEqualByComparingTo("372.78");
    assertThat(r.totale()).isNotEqualByComparingTo("1757.98");
  }

  @Test
  void ivaMistaEsentiEAccreditiSonoRiconciliati() {
    var p =
        profilo(
            voce("energia", Categoria.ENERGIA, Base.QUOTA_MESE, null, "100", "0.10", false),
            voce("rete", Categoria.TRASPORTO, Base.QUOTA_MESE, null, "50", "0.22", false));
    var r =
        calcola(
            p,
            List.of(mese("2026-01", "0", "0", "0", 1)),
            List.of(
                new Partita("Accredito", n("-10"), false, n("0.22")),
                new Partita("Esente", n("5"), true, n("0"))),
            "200");
    assertThat(r.imponibile()).isEqualByComparingTo("140");
    assertThat(r.totaleIva()).isEqualByComparingTo("18.8");
    assertThat(r.esenti()).isEqualByComparingTo("5");
    assertThat(r.totale()).isEqualByComparingTo("163.8");
    assertThat(r.iva()).hasSize(2);
  }

  @Test
  void perditeExcelSiArrotondanoPerFascia() {
    var p =
        profilo(voce("loss", Categoria.ENERGIA, Base.PERDITE_FASCIA, Fascia.F0, "1", "0", false));
    var r = calcola(p, List.of(mese("2026-01", "15", "15", "15", 1)), List.of(), "10");
    assertThat(r.righe().get(0).quantita()).isEqualByComparingTo("6");
  }

  @Test
  void consumiZeroNonAzzeranoQuotePotenzaEQuoteAnnuali() {
    var p =
        profilo(
            voce("power", Categoria.ONERI, Base.KW_MESE, null, "2", "0", false),
            voce("annual", Categoria.TRASPORTO, Base.KW_ANNO, null, "12", "0", false),
            voce("pcv", Categoria.ENERGIA, Base.QUOTA_ANNO, null, "120", "0", false));
    var r = calcola(p, List.of(mese("2026-01", "0", "0", "0", 1)), List.of(), "100");
    assertThat(r.totale()).isEqualByComparingTo("63.4");
    assertThat(calcola(p, List.of(mese("2026-01", "0", "0", "0", 0)), List.of(), "100").totale())
        .isEqualByComparingTo("0");
  }

  @Test
  void punMensileEsplicitoEMancante() {
    var p =
        profilo(
            voce("indexed", Categoria.ENERGIA, Base.KWH_FASCIA, Fascia.F1, "0.01", "0.22", true));
    assertThatThrownBy(
            () -> calcola(p, List.of(mese("2026-01", "100", "0", "0", 1)), List.of(), "100"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("PUN F1 mancante");
    var m =
        new MeseInput(
            "2026-01",
            1L,
            n("100"),
            n("0"),
            n("0"),
            1,
            new it.progettoluce.offerte.Prezzi(null, n("0.12"), null, null, null));
    assertThat(calcola(p, List.of(m), List.of(), "100").totale()).isEqualByComparingTo("15.86");
  }

  @Test
  void mesiDuplicatiNonConsecutiviEScadenzaRifiutati() {
    var p = profilo(voce("a", Categoria.ENERGIA, Base.QUOTA_MESE, null, "1", "0", false));
    assertThatThrownBy(
            () ->
                calcola(
                    p,
                    List.of(mese("2026-01", "0", "0", "0", 1), mese("2026-03", "0", "0", "0", 1)),
                    List.of(),
                    "10"))
        .hasMessageContaining("consecutivi");
    assertThatThrownBy(
            () ->
                calcola(
                    p,
                    List.of(mese("2026-01", "0", "0", "0", 1), mese("2026-01", "0", "0", "0", 1)),
                    List.of(),
                    "10"))
        .hasMessageContaining("duplicati");
    assertThatThrownBy(
            () -> calcola(p, List.of(mese("2027-01", "0", "0", "0", 1)), List.of(), "10"))
        .hasMessageContaining("non è valido");
  }

  @Test
  void totaleZeroIncidenzePercentualeEDifferenzaNegativa() {
    var zero = profilo(voce("a", Categoria.ENERGIA, Base.QUOTA_MESE, null, "0", "0", false));
    var r = calcola(zero, List.of(mese("2026-01", "0", "0", "0", 1)), List.of(), "0");
    assertThat(r.risparmioPercentuale()).isNull();
    assertThat(r.incidenze().values()).containsOnlyNulls();
    var cost = profilo(voce("a", Categoria.ENERGIA, Base.QUOTA_MESE, null, "100", "0", false));
    assertThat(
            calcola(cost, List.of(mese("2026-01", "0", "0", "0", 1)), List.of(), "50")
                .risparmioAnnualizzato())
        .isEqualByComparingTo("-600");
  }

  @Test
  void esentiConIvaENegativiInvalidi() {
    var p = profilo(voce("a", Categoria.ENERGIA, Base.QUOTA_MESE, null, "10", "0.22", false));
    assertThatThrownBy(
            () ->
                calcola(
                    p,
                    List.of(mese("2026-01", "0", "0", "0", 1)),
                    List.of(new Partita("a", n("5"), true, n("0.22"))),
                    "0"))
        .hasMessageContaining("esente");
    assertThatThrownBy(
            () ->
                calcola(
                    p,
                    List.of(mese("2026-01", "0", "0", "0", 1)),
                    List.of(new Partita("a", n("-20"), false, n("0.22"))),
                    "0"))
        .hasMessageContaining("negativo");
  }

  @Test
  void tariffeDiversePerMeseESegnalazioneBozza() {
    var p = profilo(voce("a", Categoria.ENERGIA, Base.QUOTA_MESE, null, "10", "0", false));
    var p2 =
        new Profilo(
            2L,
            3L,
            new ProfiloInput(
                "Bozza",
                "Test",
                "2026-02",
                null,
                false,
                "",
                n("0"),
                false,
                List.of(voce("a", Categoria.ENERGIA, Base.QUOTA_MESE, null, "20", "0.22", false)),
                null));
    var m2 = new MeseInput("2026-02", 2L, n("0"), n("0"), n("0"), 1, null);
    var r =
        engine.calcola(
            input(List.of(mese("2026-01", "0", "0", "0", 1), m2), List.of(), "100"),
            Map.of(1L, p, 2L, p2));
    assertThat(r.totale()).isEqualByComparingTo("34.4");
    assertThat(r.avvisi()).anyMatch(a -> a.contains("Bozza"));
    assertThat(r.mesi().get(1).profilo().versione()).isEqualTo(3L);
  }
}
