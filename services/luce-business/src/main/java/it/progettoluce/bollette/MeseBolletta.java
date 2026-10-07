package it.progettoluce.bollette;

import it.progettoluce.offerte.Prezzi;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(
    name = "mesi_bolletta",
    uniqueConstraints = @UniqueConstraint(columnNames = {"bolletta_id", "mese"}))
public class MeseBolletta extends it.bollettalab.platform.ScopedEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "bolletta_id", nullable = false)
  private BollettaConcorrente bolletta;

  @Column(nullable = false, length = 7)
  private String mese;

  @Column(nullable = false)
  private int quoteFisse = 1;

  @Column(nullable = false, precision = 14, scale = 6)
  private BigDecimal f1;

  @Column(nullable = false, precision = 14, scale = 6)
  private BigDecimal f2;

  @Column(nullable = false, precision = 14, scale = 6)
  private BigDecimal f3;

  @Embedded
  @AttributeOverrides({
    @AttributeOverride(name = "f0", column = @Column(name = "pun_f0", precision = 16, scale = 8)),
    @AttributeOverride(name = "f1", column = @Column(name = "pun_f1", precision = 16, scale = 8)),
    @AttributeOverride(name = "f23", column = @Column(name = "pun_f23", precision = 16, scale = 8)),
    @AttributeOverride(name = "f2", column = @Column(name = "pun_f2", precision = 16, scale = 8)),
    @AttributeOverride(name = "f3", column = @Column(name = "pun_f3", precision = 16, scale = 8))
  })
  private Prezzi pun;

  protected MeseBolletta() {}

  MeseBolletta(BollettaConcorrente bolletta, MeseRequest r) {
    this.bolletta = bolletta;
    aggiorna(r);
  }

  void aggiorna(MeseRequest r) {
    mese = r.mese();
    quoteFisse = r.quoteFisse();
    f1 = r.f1();
    f2 = r.f2();
    f3 = r.f3();
    pun = r.pun();
  }

  public String getMese() {
    return mese;
  }

  public BigDecimal getF1() {
    return f1;
  }

  public BigDecimal getF2() {
    return f2;
  }

  public BigDecimal getF3() {
    return f3;
  }

  public Prezzi getPun() {
    return pun;
  }

  public int getQuoteFisse() {
    return quoteFisse;
  }

  public BigDecimal totaleKwh() {
    return f1.add(f2).add(f3);
  }

  public MeseRequest dati() {
    return new MeseRequest(mese, f1, f2, f3, pun, quoteFisse);
  }
}
