package it.progettoluce.bollette;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.*;

@Entity
@Table(name = "bollette")
public class BollettaConcorrente extends it.bollettalab.platform.ScopedEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version private Long versione;

  @Column(nullable = false, length = 150)
  private String cliente;

  @Column(length = 11)
  private String partitaIva;

  @Column(nullable = false, length = 30)
  private String pod;

  @Column(nullable = false, length = 150)
  private String fornitore;

  @Column(nullable = false, precision = 8, scale = 4)
  private BigDecimal potenzaKw;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal totaleFatturato;

  @Column(nullable = false, precision = 5, scale = 4)
  private BigDecimal aliquotaIva;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal altrePartiteImponibili;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal altrePartiteEsenti;

  @OneToMany(mappedBy = "bolletta", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("mese ASC")
  private List<MeseBolletta> mesi = new ArrayList<>();

  public BollettaConcorrente() {}

  public void aggiorna(BollettaRequest r) {
    var ordinati = r.mesi().stream().sorted(Comparator.comparing(MeseRequest::mese)).toList();
    for (int i = 1; i < ordinati.size(); i++) {
      if (!java.time.YearMonth.parse(ordinati.get(i - 1).mese())
          .plusMonths(1)
          .toString()
          .equals(ordinati.get(i).mese()))
        throw new IllegalArgumentException("I mesi devono essere consecutivi e senza duplicati");
    }
    cliente = r.cliente().trim();
    partitaIva = r.partitaIva();
    pod = r.pod().trim();
    fornitore = r.fornitore().trim();
    potenzaKw = r.potenzaKw();
    totaleFatturato = r.totaleFatturato();
    aliquotaIva = r.aliquotaIva();
    altrePartiteImponibili = r.altrePartiteImponibili();
    altrePartiteEsenti = r.altrePartiteEsenti();
    // Preserve existing rows with the same month to avoid delete/insert uniqueness races.
    mesi.removeIf(m -> ordinati.stream().noneMatch(rm -> rm.mese().equals(m.getMese())));
    for (MeseRequest rMese : ordinati) {
      var esistente = mesi.stream().filter(m -> m.getMese().equals(rMese.mese())).findFirst();
      if (esistente.isPresent()) esistente.get().aggiorna(rMese);
      else mesi.add(new MeseBolletta(this, rMese));
    }
    mesi.sort(Comparator.comparing(MeseBolletta::getMese));
  }

  public Long getId() {
    return id;
  }

  public Long getVersione() {
    return versione;
  }

  public String getCliente() {
    return cliente;
  }

  public BigDecimal getPotenzaKw() {
    return potenzaKw;
  }

  public BigDecimal getTotaleFatturato() {
    return totaleFatturato;
  }

  public BigDecimal getAliquotaIva() {
    return aliquotaIva;
  }

  public BigDecimal getAltrePartiteImponibili() {
    return altrePartiteImponibili;
  }

  public BigDecimal getAltrePartiteEsenti() {
    return altrePartiteEsenti;
  }

  public List<MeseBolletta> getMesi() {
    return List.copyOf(mesi);
  }

  public BollettaRequest dati() {
    return new BollettaRequest(
        cliente,
        pod,
        fornitore,
        potenzaKw,
        totaleFatturato,
        aliquotaIva,
        altrePartiteImponibili,
        altrePartiteEsenti,
        mesi.stream().map(MeseBolletta::dati).toList(),
        versione,
        partitaIva);
  }
}
