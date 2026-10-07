package it.progettoluce.calcolo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record RisultatoCalcolo(
    List<Riga> righe,
    Map<String, BigDecimal> categorie,
    BigDecimal imponibile,
    BigDecimal iva,
    BigDecimal altrePartiteEsenti,
    BigDecimal totale,
    BigDecimal risparmioPeriodo,
    BigDecimal risparmioPercentuale,
    BigDecimal stimaRisparmioAnnuale,
    int numeroMesi,
    String notaStima) {
  public record Riga(
      String mese,
      String descrizione,
      String categoria,
      String unita,
      BigDecimal quantita,
      BigDecimal corrispettivo,
      BigDecimal importo) {}
}
