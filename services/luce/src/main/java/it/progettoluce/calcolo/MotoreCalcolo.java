package it.progettoluce.calcolo;

import static it.progettoluce.offerte.VoceCorrispettivo.TipoVoce.*;

import it.progettoluce.bollette.*;
import it.progettoluce.offerte.*;
import it.progettoluce.parametri.ParametriRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * Pure engine. All rates come from the saved offer, explicit monthly PUN and configured profile.
 */
@Component
public class MotoreCalcolo {
  private static final BigDecimal DODICI = new BigDecimal("12");

  public RisultatoCalcolo calcola(BollettaConcorrente b, Offerta o, ParametriRequest p) {
    return calcola(
        b,
        o,
        b.getMesi().stream()
            .collect(java.util.stream.Collectors.toMap(MeseBolletta::getMese, m -> p)));
  }

  public RisultatoCalcolo calcola(
      BollettaConcorrente b, Offerta o, Map<String, ParametriRequest> profili) {
    if (!o.isAttiva()) throw new IllegalArgumentException("L'offerta è disattivata");
    if (o.getVoci().isEmpty())
      throw new IllegalArgumentException("L'offerta non ha voci di calcolo");
    List<RisultatoCalcolo.Riga> righe = new ArrayList<>();
    Map<String, BigDecimal> categorie = new LinkedHashMap<>();
    for (String categoria : List.of("ENERGIA", "TRASPORTO", "ONERI", "IMPOSTE", "ALTRE_PARTITE"))
      categorie.put(categoria, BigDecimal.ZERO);
    for (MeseBolletta m : b.getMesi()) {
      ParametriRequest p = profili.get(m.getMese());
      if (p == null)
        throw new IllegalArgumentException("Profilo mensile mancante per " + m.getMese());
      BigDecimal netti = m.totaleKwh();
      BigDecimal perdite =
          perdita(m.getF1(), p).add(perdita(m.getF2(), p)).add(perdita(m.getF3(), p));
      for (VoceCorrispettivo v : o.getVoci()) {
        if (v.getTipo() == PCV) {
          BigDecimal mensile = v.getCorrispettivo().divide(DODICI, 12, RoundingMode.HALF_UP);
          aggiungi(
              righe,
              categorie,
              m.getMese(),
              "Commercializzazione (PCV)",
              "ENERGIA",
              "€/mese",
              BigDecimal.ONE,
              mensile);
        } else if (v.getTipo() == ENERGIA) {
          BigDecimal qta = quantita(v.getFascia(), m, p);
          BigDecimal prezzo = v.getCorrispettivo();
          if (v.isIndicizzata()) {
            BigDecimal pun = m.getPun() == null ? null : m.getPun().valore(v.getFascia());
            if (pun == null)
              throw new IllegalArgumentException(
                  "PUN " + v.getFascia() + " mancante per " + m.getMese() + " (€/kWh)");
            prezzo = prezzo.add(pun);
          }
          aggiungi(
              righe,
              categorie,
              m.getMese(),
              "Energia " + v.getFascia() + (v.isIndicizzata() ? " · PUN + spread" : ""),
              "ENERGIA",
              "€/kWh",
              qta,
              prezzo);
        }
      }
      aggiungi(
          righe,
          categorie,
          m.getMese(),
          "Dispacciamento",
          "ENERGIA",
          "€/kWh",
          netti.add(perdite),
          p.dispacciamentoKwh());
      aggiungi(
          righe,
          categorie,
          m.getMese(),
          "Trasporto · quota fissa",
          "TRASPORTO",
          "€/mese",
          BigDecimal.ONE,
          p.trasportoFissoMese());
      aggiungi(
          righe,
          categorie,
          m.getMese(),
          "Trasporto · potenza",
          "TRASPORTO",
          "€/kW/mese",
          b.getPotenzaKw(),
          p.trasportoPotenzaAnno().divide(DODICI, 12, RoundingMode.HALF_UP));
      aggiungi(
          righe,
          categorie,
          m.getMese(),
          "Trasporto · energia",
          "TRASPORTO",
          "€/kWh",
          netti,
          p.trasportoKwh());
      aggiungi(
          righe,
          categorie,
          m.getMese(),
          "Oneri · quota fissa",
          "ONERI",
          "€/mese",
          BigDecimal.ONE,
          p.oneriFissiMese());
      aggiungi(
          righe, categorie, m.getMese(), "Oneri · energia", "ONERI", "€/kWh", netti, p.oneriKwh());
      aggiungi(
          righe,
          categorie,
          m.getMese(),
          "Accisa configurata",
          "IMPOSTE",
          "€/kWh",
          netti,
          p.accisaKwh());
    }
    aggiungi(
        righe,
        categorie,
        "Periodo",
        "Altre partite imponibili",
        "ALTRE_PARTITE",
        "€",
        BigDecimal.ONE,
        b.getAltrePartiteImponibili());
    BigDecimal imponibile =
        soldi(categorie.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    if (imponibile.signum() < 0)
      throw new IllegalArgumentException(
          "Le altre partite producono un imponibile negativo: verifica gli importi");
    BigDecimal iva = soldi(imponibile.multiply(b.getAliquotaIva()));
    BigDecimal totale = soldi(imponibile.add(iva).add(b.getAltrePartiteEsenti()));
    BigDecimal risparmio = soldi(b.getTotaleFatturato().subtract(totale));
    BigDecimal percentuale =
        b.getTotaleFatturato().signum() == 0
            ? null
            : risparmio
                .multiply(new BigDecimal("100"))
                .divide(b.getTotaleFatturato(), 2, RoundingMode.HALF_UP);
    int mesi = b.getMesi().size();
    BigDecimal stima =
        risparmio.multiply(DODICI).divide(BigDecimal.valueOf(mesi), 2, RoundingMode.HALF_UP);
    return new RisultatoCalcolo(
        List.copyOf(righe),
        Collections.unmodifiableMap(categorie),
        imponibile,
        iva,
        b.getAltrePartiteEsenti(),
        totale,
        risparmio,
        percentuale,
        stima,
        mesi,
        "Proiezione indicativa: risparmio del periodo × 12 / mesi. Non considera stagionalità o"
            + " variazioni future del PUN. Le altre partite sono riportate su entrambi i lati.");
  }

  private BigDecimal quantita(Fascia fascia, MeseBolletta m, ParametriRequest p) {
    BigDecimal f1 = m.getF1().add(perdita(m.getF1(), p));
    BigDecimal f2 = m.getF2().add(perdita(m.getF2(), p));
    BigDecimal f3 = m.getF3().add(perdita(m.getF3(), p));
    return switch (fascia) {
      case F0 -> f1.add(f2).add(f3);
      case F1 -> f1;
      case F23 -> f2.add(f3);
      case F2 -> f2;
      case F3 -> f3;
    };
  }

  private BigDecimal perdita(BigDecimal kwh, ParametriRequest p) {
    return kwh.multiply(p.coefficientePerdite())
        .setScale(p.arrotondaPerdite() ? 0 : 8, RoundingMode.HALF_UP);
  }

  private void aggiungi(
      List<RisultatoCalcolo.Riga> righe,
      Map<String, BigDecimal> categorie,
      String mese,
      String descrizione,
      String categoria,
      String unita,
      BigDecimal quantita,
      BigDecimal prezzo) {
    BigDecimal importo = quantita.multiply(prezzo).setScale(8, RoundingMode.HALF_UP);
    righe.add(
        new RisultatoCalcolo.Riga(mese, descrizione, categoria, unita, quantita, prezzo, importo));
    categorie.merge(categoria, importo, BigDecimal::add);
  }

  private BigDecimal soldi(BigDecimal valore) {
    return valore.setScale(2, RoundingMode.HALF_UP);
  }
}
