package it.progettoluce.business;

import static it.progettoluce.business.BusinessModels.*;

import java.math.*;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class BusinessEngine {
  private static final BigDecimal DODICI = new BigDecimal("12");

  public Risultato calcola(SimulazioneInput input, Map<Long, Profilo> profili) {
    if (!input.confermaConfrontabilita())
      throw new IllegalArgumentException(
          "Conferma che la fattura precedente riguarda gli stessi consumi, periodo e servizi.");
    var mesi = input.mesi().stream().sorted(Comparator.comparing(MeseInput::mese)).toList();
    for (int i = 1; i < mesi.size(); i++)
      if (!YearMonth.parse(mesi.get(i - 1).mese())
          .plusMonths(1)
          .equals(YearMonth.parse(mesi.get(i).mese())))
        throw new IllegalArgumentException("I mesi devono essere consecutivi e senza duplicati.");
    List<Riga> righe = new ArrayList<>();
    List<Mensile> mensili = new ArrayList<>();
    LinkedHashSet<String> avvisi = new LinkedHashSet<>();
    Map<Categoria, BigDecimal> categorie = nuoveCategorie();
    for (var mese : mesi) {
      var profilo = profili.get(mese.profiloId());
      if (profilo == null)
        throw new IllegalArgumentException("Profilo non trovato per " + mese.mese());
      var p = profilo.dati();
      if (mese.mese().compareTo(p.dal()) < 0
          || (p.al() != null && mese.mese().compareTo(p.al()) > 0))
        throw new IllegalArgumentException(
            "Il profilo " + p.nome() + " non è valido per " + mese.mese());
      if (!p.verificato())
        avvisi.add("Profilo non verificato: " + p.nome() + ". Risultato in bozza.");
      var catMese = nuoveCategorie();
      for (var voce : p.voci()) {
        BigDecimal prezzo = voce.corrispettivo();
        if (voce.indicizzata()) {
          BigDecimal pun = pun(mese, voce.fascia());
          if (pun == null)
            throw new IllegalArgumentException(
                "PUN " + voce.fascia() + " mancante per " + mese.mese() + ". Inserisci €/kWh.");
          prezzo = prezzo.add(pun);
        }
        BigDecimal q = quantita(mese, input.potenzaKw(), p, voce);
        BigDecimal importo = q.multiply(prezzo).setScale(8, RoundingMode.HALF_UP);
        righe.add(
            new Riga(
                mese.mese(),
                voce.codice(),
                voce.descrizione(),
                voce.categoria(),
                unita(voce.base()),
                q,
                prezzo,
                importo,
                voce.aliquotaIva(),
                false));
        categorie.merge(voce.categoria(), importo, BigDecimal::add);
        catMese.merge(voce.categoria(), importo, BigDecimal::add);
      }
      mensili.add(
          new Mensile(
              mese.mese(),
              profilo,
              Collections.unmodifiableMap(catMese),
              soldi(somma(catMese.values()))));
    }
    for (int i = 0; i < input.altrePartite().size(); i++) {
      var a = input.altrePartite().get(i);
      if (a.esente() && a.aliquotaIva().signum() != 0)
        throw new IllegalArgumentException("Una partita esente deve avere aliquota IVA zero.");
      righe.add(
          new Riga(
              "Periodo",
              "PARTITA_" + i,
              a.descrizione(),
              Categoria.ALTRE_PARTITE,
              "€",
              BigDecimal.ONE,
              a.importo(),
              a.importo(),
              a.aliquotaIva(),
              a.esente()));
      categorie.merge(Categoria.ALTRE_PARTITE, a.importo(), BigDecimal::add);
    }
    Map<BigDecimal, BigDecimal> basiIva = new TreeMap<>();
    BigDecimal esenti = BigDecimal.ZERO;
    for (var r : righe) {
      if (r.esente()) esenti = esenti.add(r.imponibile());
      else basiIva.merge(r.aliquotaIva().stripTrailingZeros(), r.imponibile(), BigDecimal::add);
    }
    List<Iva> iva = new ArrayList<>();
    for (var entry : basiIva.entrySet()) {
      var base = soldi(entry.getValue());
      if (base.signum() < 0)
        throw new IllegalArgumentException(
            "Gli accrediti producono un imponibile negativo per IVA "
                + entry.getKey()
                + ". Verifica gli importi.");
      iva.add(new Iva(entry.getKey(), base, soldi(base.multiply(entry.getKey()))));
    }
    BigDecimal imponibile = somma(iva.stream().map(Iva::imponibile).toList());
    BigDecimal totaleIva = somma(iva.stream().map(Iva::imposta).toList());
    esenti = soldi(esenti);
    BigDecimal totale = soldi(imponibile.add(totaleIva).add(esenti));
    if (totale.signum() < 0)
      throw new IllegalArgumentException("Il totale simulato non può essere negativo.");
    BigDecimal risparmio = soldi(input.fatturaPrecedente().subtract(totale));
    BigDecimal percentuale =
        input.fatturaPrecedente().signum() == 0
            ? null
            : risparmio
                .multiply(new BigDecimal("100"))
                .divide(input.fatturaPrecedente(), 2, RoundingMode.HALF_UP);
    Map<Categoria, BigDecimal> incidenze = new LinkedHashMap<>();
    for (var e : categorie.entrySet())
      incidenze.put(
          e.getKey(),
          totale.signum() == 0
              ? null
              : e.getValue()
                  .multiply(new BigDecimal("100"))
                  .divide(totale, 2, RoundingMode.HALF_UP));
    avvisi.add(
        "Stima parametrica: tariffe e fiscalità devono essere verificate per questa fornitura. Non"
            + " è una fattura.");
    avvisi.add(
        "Annualizzazione = differenza del periodo × 12 / numero di mesi; non considera stagionalità"
            + " o variazioni future del PUN.");
    if (!input.altrePartite().isEmpty())
      avvisi.add(
          "Le altre partite sono importi del costo proposto: verifica che siano applicabili e"
              + " confrontabili con la fattura precedente.");
    return new Risultato(
        List.copyOf(righe),
        List.copyOf(mensili),
        Collections.unmodifiableMap(categorie),
        Collections.unmodifiableMap(incidenze),
        List.copyOf(iva),
        imponibile,
        totaleIva,
        esenti,
        totale,
        risparmio,
        percentuale,
        risparmio.multiply(DODICI).divide(BigDecimal.valueOf(mesi.size()), 2, RoundingMode.HALF_UP),
        mesi.size(),
        List.copyOf(avvisi));
  }

  private BigDecimal quantita(MeseInput m, BigDecimal kw, ProfiloInput p, Voce v) {
    BigDecimal f1 = m.f1(), f2 = m.f2(), f3 = m.f3();
    BigDecimal perdite = perdita(f1, p).add(perdita(f2, p)).add(perdita(f3, p));
    return switch (v.base()) {
      case KWH_FASCIA -> fascia(f1, f2, f3, v.fascia());
      case PERDITE_FASCIA -> fascia(perdita(f1, p), perdita(f2, p), perdita(f3, p), v.fascia());
      case KWH_NETTI -> f1.add(f2).add(f3);
      case KWH_CON_PERDITE -> f1.add(f2).add(f3).add(perdite);
      case KW_MESE -> kw.multiply(BigDecimal.valueOf(m.quoteFisse()));
      case KW_ANNO ->
          kw.multiply(BigDecimal.valueOf(m.quoteFisse())).divide(DODICI, 12, RoundingMode.HALF_UP);
      case QUOTA_MESE -> BigDecimal.valueOf(m.quoteFisse());
      case QUOTA_ANNO ->
          BigDecimal.valueOf(m.quoteFisse()).divide(DODICI, 12, RoundingMode.HALF_UP);
    };
  }

  private BigDecimal perdita(BigDecimal kwh, ProfiloInput p) {
    return kwh.multiply(p.perdite()).setScale(p.arrotondaPerdite() ? 0 : 8, RoundingMode.HALF_UP);
  }

  private BigDecimal fascia(BigDecimal a, BigDecimal b, BigDecimal c, Fascia f) {
    if (f == null) throw new IllegalArgumentException("Fascia obbligatoria per energia/perdite.");
    return switch (f) {
      case F0 -> a.add(b).add(c);
      case F1 -> a;
      case F2 -> b;
      case F3 -> c;
      case F23 -> b.add(c);
    };
  }

  private BigDecimal pun(MeseInput m, Fascia f) {
    return m.pun() == null || f == null
        ? null
        : m.pun().valore(it.progettoluce.offerte.Fascia.valueOf(f.name()));
  }

  static String unita(Base b) {
    return switch (b) {
      case KWH_FASCIA, PERDITE_FASCIA, KWH_NETTI, KWH_CON_PERDITE -> "€/kWh";
      case KW_MESE -> "€/kW/mese";
      case KW_ANNO -> "€/kW/anno";
      case QUOTA_MESE -> "€/POD/mese";
      case QUOTA_ANNO -> "€/POD/anno";
    };
  }

  static Map<Categoria, BigDecimal> nuoveCategorie() {
    var m = new LinkedHashMap<Categoria, BigDecimal>();
    for (var c : Categoria.values()) m.put(c, BigDecimal.ZERO);
    return m;
  }

  static BigDecimal soldi(BigDecimal n) {
    return n.setScale(2, RoundingMode.HALF_UP);
  }

  private BigDecimal somma(Collection<BigDecimal> n) {
    return n.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
