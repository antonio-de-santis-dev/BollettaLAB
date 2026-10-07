package it.progettoluce.calcolo;

import static org.assertj.core.api.Assertions.*;

import it.progettoluce.bollette.*;
import it.progettoluce.offerte.*;
import it.progettoluce.parametri.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class MotoreCalcoloTest {
  private final MotoreCalcolo motore = new MotoreCalcolo();

  @Test
  void indicizzataUsaPunDiversiPerOgniMese() {
    var b =
        bolletta(
            List.of(
                mese("2026-01", "100", "0", "0", "0.1"), mese("2026-02", "100", "0", "0", "0.2")),
            "0",
            "0");
    var r =
        motore.calcola(
            b, offerta(TipoOfferta.INDICIZZATA_PUN, "0.01", "120"), parametri("0", false));
    assertThat(r.imponibile()).isEqualByComparingTo("52");
    assertThat(r.totale()).isEqualByComparingTo("57.20");
    assertThat(r.risparmioPeriodo()).isEqualByComparingTo("142.80");
    assertThat(r.stimaRisparmioAnnuale()).isEqualByComparingTo("856.80");
  }

  @Test
  void perditeArrotondatePerFasciaNonSulPrezzo() {
    var b = bolletta(List.of(mese("2026-01", "24", "37", "33", null)), "0", "0");
    var r = motore.calcola(b, offerta(TipoOfferta.PREZZO_FISSO, "1", "0"), parametri("0.1", true));
    assertThat(r.righe().get(0).quantita()).isEqualByComparingTo("103");
    assertThat(r.imponibile()).isEqualByComparingTo("103");
  }

  @Test
  void perditeFrazionarieQuandoFlagDisattivato() {
    var b = bolletta(List.of(mese("2026-01", "24", "37", "33", null)), "0", "0");
    var r = motore.calcola(b, offerta(TipoOfferta.PREZZO_FISSO, "1", "0"), parametri("0.1", false));
    assertThat(r.imponibile()).isEqualByComparingTo("103.40");
  }

  @Test
  void altrePartiteImponibiliPrimaIvaEdEsentiDopo() {
    var b = bolletta(List.of(mese("2026-01", "100", "0", "0", null)), "5", "-3");
    var r = motore.calcola(b, offerta(TipoOfferta.PREZZO_FISSO, "0.2", "0"), parametri("0", false));
    assertThat(r.imponibile()).isEqualByComparingTo("25");
    assertThat(r.iva()).isEqualByComparingTo("2.50");
    assertThat(r.totale()).isEqualByComparingTo("24.50");
  }

  @Test
  void consumoZeroNonCausaDivisionePerZero() {
    var b = bolletta(List.of(mese("2026-01", "0", "0", "0", null)), "0", "0");
    var r =
        motore.calcola(b, offerta(TipoOfferta.PREZZO_FISSO, "0.2", "120"), parametri("0", false));
    assertThat(r.totale()).isEqualByComparingTo("11");
  }

  @Test
  void rifiutaPunMancanteInveceDiInventareValore() {
    var b = bolletta(List.of(mese("2026-01", "100", "0", "0", null)), "0", "0");
    assertThatThrownBy(
            () ->
                motore.calcola(
                    b, offerta(TipoOfferta.INDICIZZATA_PUN, "0.01", "0"), parametri("0", false)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("PUN F0 mancante");
  }

  @Test
  void offertaSenzaVociNonPuòProdurreFalsoRisparmio() {
    var o = new Offerta();
    o.aggiorna(
        new OffertaRequest(
            "F",
            "O",
            TipoOfferta.PREZZO_FISSO,
            TipoTariffa.MONORARIA,
            new Prezzi(),
            null,
            b("0"),
            true,
            "",
            null),
        List.of());
    assertThatThrownBy(
            () ->
                motore.calcola(
                    bolletta(List.of(mese("2026-01", "100", "0", "0", null)), "0", "0"),
                    o,
                    parametri("0", false)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("non ha voci");
  }

  @Test
  void pcvAnnuaMantienePrecisionePrimaDivisione() {
    var r =
        motore.calcola(
            bolletta(List.of(mese("2026-01", "0", "0", "0", null)), "0", "0"),
            offerta(TipoOfferta.PREZZO_FISSO, "0", "1"),
            parametri("0", false));
    assertThat(r.imponibile()).isEqualByComparingTo("0.08");
    assertThat(r.iva()).isEqualByComparingTo("0.01");
  }

  @Test
  void bollettaZeroRestituiscePercentualeNonDisponibile() {
    var b = new BollettaConcorrente();
    b.aggiorna(
        new BollettaRequest(
            "C",
            "POD",
            "F",
            b("3"),
            b("0"),
            b("0.1"),
            b("0"),
            b("0"),
            List.of(mese("2026-01", "0", "0", "0", null)),
            null));
    assertThat(
            motore
                .calcola(b, offerta(TipoOfferta.PREZZO_FISSO, "0", "0"), parametri("0", false))
                .risparmioPercentuale())
        .isNull();
  }

  @Test
  void calcolaQuotePotenzaDaAnnoAMeseENettiPerTrasporto() {
    var p =
        new ParametriRequest(
            "Test", "Test", b("0.1"), false, b("0"), b("2"), b("12"), b("0.1"), b("1"), b("0.2"),
            b("0.01"), null);
    var r =
        motore.calcola(
            bolletta(List.of(mese("2026-01", "100", "0", "0", null)), "0", "0"),
            offerta(TipoOfferta.PREZZO_FISSO, "0", "0"),
            p);
    assertThat(r.categorie().get("TRASPORTO")).isEqualByComparingTo("15");
    assertThat(r.categorie().get("ONERI")).isEqualByComparingTo("21");
    assertThat(r.categorie().get("IMPOSTE")).isEqualByComparingTo("1");
  }

  private Offerta offerta(TipoOfferta tipo, String prezzo, String pcv) {
    var valori = new Prezzi(b(prezzo), null, null, null, null);
    var request =
        new OffertaRequest(
            "F", "O", tipo, TipoTariffa.MONORARIA, valori, valori, b(pcv), true, "", null);
    var o = new Offerta();
    o.aggiorna(request, new OffertaVociFactory().crea(request));
    return o;
  }

  private BollettaConcorrente bolletta(List<MeseRequest> mesi, String imponibili, String esenti) {
    var b = new BollettaConcorrente();
    b.aggiorna(
        new BollettaRequest(
            "C", "POD", "F", b("3"), b("200"), b("0.1"), b(imponibili), b(esenti), mesi, null));
    return b;
  }

  private MeseRequest mese(String mese, String f1, String f2, String f3, String pun) {
    return new MeseRequest(
        mese, b(f1), b(f2), b(f3), pun == null ? null : new Prezzi(b(pun), null, null, null, null));
  }

  private ParametriRequest parametri(String perdite, boolean arrotonda) {
    return new ParametriRequest(
        "Test",
        "Test",
        b(perdite),
        arrotonda,
        b("0"),
        b("0"),
        b("0"),
        b("0"),
        b("0"),
        b("0"),
        b("0"),
        null);
  }

  private static BigDecimal b(String v) {
    return new BigDecimal(v);
  }
}
