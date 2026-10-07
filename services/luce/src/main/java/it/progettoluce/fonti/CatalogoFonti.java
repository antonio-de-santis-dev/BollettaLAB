package it.progettoluce.fonti;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;

public final class CatalogoFonti {
  private CatalogoFonti() {}

  public static final String RESIDENTE = "DOMESTICO_RESIDENTE",
      NON_RESIDENTE = "DOMESTICO_NON_RESIDENTE",
      INDICE = "INDICE",
      ORIGINALE = "ORIGINALE";
  public static final Map<String, String> UNITA =
      Map.ofEntries(
          Map.entry("coefficientePerdite", "frazione"),
          Map.entry("dispacciamentoKwh", "€/kWh con perdite"),
          Map.entry("trasportoFissoMese", "€/mese"),
          Map.entry("trasportoPotenzaAnno", "€/kW/anno"),
          Map.entry("trasportoKwh", "€/kWh"),
          Map.entry("oneriFissiMese", "€/mese"),
          Map.entry("oneriKwh", "€/kWh"),
          Map.entry("accisaKwh", "€/kWh · aliquota base"),
          Map.entry("aliquotaIva", "frazione"),
          Map.entry("PUN_F0", "€/kWh"),
          Map.entry("PUN_F1", "€/kWh"),
          Map.entry("PUN_F2", "€/kWh"),
          Map.entry("PUN_F3", "€/kWh"),
          Map.entry("PUN_F23", "€/kWh"));

  public static void valida(String codice, String periodo, String categoria, BigDecimal valore) {
    if (!periodo.matches("[0-9]{4}-(0[1-9]|1[0-2])"))
      throw new IllegalArgumentException("Periodo richiesto: AAAA-MM");
    YearMonth.parse(periodo);
    if (!UNITA.containsKey(codice))
      throw new IllegalArgumentException("Parametro non modificabile");
    if (codice.startsWith("PUN_")
        ? !INDICE.equals(categoria)
        : !Set.of(RESIDENTE, NON_RESIDENTE).contains(categoria))
      throw new IllegalArgumentException("Categoria non compatibile con il parametro");
    if (valore == null
        || valore.signum() < 0
        || valore.compareTo(new BigDecimal("99999999")) > 0
        || valore.scale() > 8)
      throw new IllegalArgumentException("Valore non negativo con al massimo otto decimali");
    if (Set.of("coefficientePerdite", "aliquotaIva").contains(codice)
        && valore.compareTo(BigDecimal.ONE) > 0)
      throw new IllegalArgumentException("Inserisci una frazione tra zero e uno");
  }
}
