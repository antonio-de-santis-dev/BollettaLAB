package it.progettoluce.fonti;

import it.progettoluce.bollette.*;
import it.progettoluce.offerte.*;
import it.progettoluce.parametri.ParametriRequest;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ParametriUfficiali {
  public record Mensile(String mese, ParametriRequest parametri, List<ArchivioFonti.Dato> fonti) {}

  public record Preparato(BollettaConcorrente bolletta, List<Mensile> mesi) {}

  private final ArchivioFonti archivio;

  public ParametriUfficiali(ArchivioFonti archivio) {
    this.archivio = archivio;
  }

  public Preparato prepara(BollettaRequest b, Offerta offerta, String categoria) {
    if (!Set.of(CatalogoFonti.RESIDENTE, CatalogoFonti.NON_RESIDENTE)
        .contains(categoria == null ? "" : categoria))
      throw new IllegalArgumentException("Seleziona il profilo domestico");
    List<Mensile> profili = new ArrayList<>();
    List<MeseRequest> consumi = new ArrayList<>();
    BigDecimal iva = null;
    for (MeseRequest m : b.mesi()) {
      var dati = archivio.lista(m.mese(), categoria);
      Map<String, ArchivioFonti.Dato> valori = new HashMap<>();
      dati.forEach(d -> valori.put(d.codice(), d));
      var a = necessario(valori, "accisaKwh", m.mese());
      if (categoria.equals(CatalogoFonti.RESIDENTE)
          && b.potenzaKw().compareTo(new BigDecimal("3")) <= 0
          && a.valoreManuale() == null)
        throw new IllegalArgumentException(
            "Accisa: per residenti fino a 3 kW occorre verificare esenzioni e scaglioni. Inserisci"
                + " in Fonti ufficiali un'aliquota effettiva manuale motivata per "
                + m.mese());
      var aliquota = necessario(valori, "aliquotaIva", m.mese()).valore();
      if (iva != null && iva.compareTo(aliquota) != 0)
        throw new IllegalArgumentException(
            "Aliquote IVA diverse nel periodo: separa le bollette per aliquota");
      iva = aliquota;
      var p =
          new ParametriRequest(
              "Standard domestico · " + m.mese(),
              "Portale Offerte / eventuali correzioni manuali · " + categoria,
              valore(valori, "coefficientePerdite", m.mese()),
              false,
              valore(valori, "dispacciamentoKwh", m.mese()),
              valore(valori, "trasportoFissoMese", m.mese()),
              valore(valori, "trasportoPotenzaAnno", m.mese()),
              valore(valori, "trasportoKwh", m.mese()),
              valore(valori, "oneriFissiMese", m.mese()),
              valore(valori, "oneriKwh", m.mese()),
              a.valore(),
              null);
      List<ArchivioFonti.Dato> usati = new ArrayList<>(dati);
      boolean indicizzata = offerta.getVoci().stream().anyMatch(v -> v.isIndicizzata());
      Prezzi pun = m.pun();
      if (indicizzata) {
        var indici = archivio.lista(m.mese(), CatalogoFonti.INDICE);
        Map<String, ArchivioFonti.Dato> punMap = new HashMap<>();
        indici.forEach(d -> punMap.put(d.codice(), d));
        for (var voce : offerta.getVoci())
          if (voce.isIndicizzata()) necessario(punMap, "PUN_" + voce.getFascia(), m.mese());
        pun =
            new Prezzi(
                opzionale(punMap, "PUN_F0"),
                opzionale(punMap, "PUN_F1"),
                opzionale(punMap, "PUN_F23"),
                opzionale(punMap, "PUN_F2"),
                opzionale(punMap, "PUN_F3"));
        usati.addAll(indici);
      }
      consumi.add(new MeseRequest(m.mese(), m.f1(), m.f2(), m.f3(), pun));
      profili.add(new Mensile(m.mese(), p, List.copyOf(usati)));
    }
    var effettiva = new BollettaConcorrente();
    effettiva.aggiorna(
        new BollettaRequest(
            b.cliente(),
            b.pod(),
            b.fornitore(),
            b.potenzaKw(),
            b.totaleFatturato(),
            iva,
            b.altrePartiteImponibili(),
            b.altrePartiteEsenti(),
            consumi,
            null));
    return new Preparato(effettiva, List.copyOf(profili));
  }

  private ArchivioFonti.Dato necessario(
      Map<String, ArchivioFonti.Dato> m, String codice, String periodo) {
    var d = m.get(codice);
    if (d == null || d.valore() == null)
      throw new IllegalArgumentException(
          "Parametro "
              + codice
              + " mancante per "
              + periodo
              + ". Importa o inserisci il dato in Fonti ufficiali.");
    return d;
  }

  private BigDecimal valore(Map<String, ArchivioFonti.Dato> m, String c, String periodo) {
    return necessario(m, c, periodo).valore();
  }

  private BigDecimal opzionale(Map<String, ArchivioFonti.Dato> m, String c) {
    return m.containsKey(c) ? m.get(c).valore() : null;
  }
}
