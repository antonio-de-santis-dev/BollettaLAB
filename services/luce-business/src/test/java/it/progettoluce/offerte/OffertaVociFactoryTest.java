package it.progettoluce.offerte;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class OffertaVociFactoryTest {
  private final OffertaVociFactory factory = new OffertaVociFactory();
  private final Prezzi prezzi = new Prezzi(b("0.11"), b("0.12"), b("0.13"), b("0.14"), b("0.15"));

  @ParameterizedTest
  @CsvSource({
    "PREZZO_FISSO,MONORARIA,2",
    "PREZZO_FISSO,BIORARIA,3",
    "PREZZO_FISSO,TRIORARIA,4",
    "INDICIZZATA_PUN,MONORARIA,2",
    "INDICIZZATA_PUN,BIORARIA,3",
    "INDICIZZATA_PUN,TRIORARIA,4"
  })
  void costruisceTutteLeCombinazioni(TipoOfferta tipo, TipoTariffa tariffa, int numero) {
    var r = request(tipo, tariffa, prezzi, b("120"));
    var voci = factory.crea(r);
    assertThat(voci).hasSize(numero);
    assertThat(voci.get(numero - 1).getCorrispettivo()).isEqualByComparingTo("120");
    assertThat(voci.get(numero - 1).getTipo()).isEqualTo(VoceCorrispettivo.TipoVoce.PCV);
    voci.stream()
        .filter(v -> v.getTipo() == VoceCorrispettivo.TipoVoce.ENERGIA)
        .forEach(
            v -> {
              assertThat(v.isIndicizzata()).isEqualTo(tipo == TipoOfferta.INDICIZZATA_PUN);
              assertThat(v.getCorrispettivo()).isEqualByComparingTo(prezzi.valore(v.getFascia()));
            });
    assertThat(
            voci.stream()
                .filter(v -> v.getTipo() == VoceCorrispettivo.TipoVoce.ENERGIA)
                .map(VoceCorrispettivo::getFascia))
        .containsExactlyElementsOf(
            switch (tariffa) {
              case MONORARIA -> java.util.List.of(Fascia.F0);
              case BIORARIA -> java.util.List.of(Fascia.F1, Fascia.F23);
              case TRIORARIA -> java.util.List.of(Fascia.F1, Fascia.F2, Fascia.F3);
            });
  }

  @ParameterizedTest
  @EnumSource(TipoTariffa.class)
  void rifiutaFasceMancanti(TipoTariffa tariffa) {
    assertThatThrownBy(
            () -> factory.crea(request(TipoOfferta.PREZZO_FISSO, tariffa, new Prezzi(), b("0"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("obbligatorio");
  }

  @Test
  void rifiutaPrezziNegativi() {
    assertThatThrownBy(
            () ->
                factory.crea(
                    request(
                        TipoOfferta.PREZZO_FISSO,
                        TipoTariffa.MONORARIA,
                        new Prezzi(b("-1"), null, null, null, null),
                        b("0"))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rifiutaPcvMancante() {
    assertThatThrownBy(
            () ->
                factory.crea(
                    request(TipoOfferta.PREZZO_FISSO, TipoTariffa.MONORARIA, prezzi, null)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void zeroEUnPrezzoEsplicitoValido() {
    var voci =
        factory.crea(
            request(
                TipoOfferta.PREZZO_FISSO,
                TipoTariffa.MONORARIA,
                new Prezzi(b("0"), null, null, null, null),
                b("0")));
    assertThat(voci.get(0).getCorrispettivo()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  private OffertaRequest request(
      TipoOfferta tipo, TipoTariffa tariffa, Prezzi valori, BigDecimal pcv) {
    return new OffertaRequest(
        "Fornitore", "Offerta", tipo, tariffa, valori, valori, pcv, true, "", null);
  }

  private static BigDecimal b(String v) {
    return new BigDecimal(v);
  }
}
