package it.progettoluce.offerte;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "voci_corrispettivo")
public class VoceCorrispettivo extends it.bollettalab.platform.ScopedEntity {
  public enum TipoVoce {
    ENERGIA,
    PCV
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "offerta_id", nullable = false)
  private Offerta offerta;

  @Enumerated(EnumType.STRING)
  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
  @Column(nullable = false)
  private TipoVoce tipo;

  @Enumerated(EnumType.STRING)
  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
  private Fascia fascia;

  @Column(nullable = false, precision = 16, scale = 8)
  private BigDecimal corrispettivo;

  @Column(nullable = false)
  private boolean indicizzata;

  protected VoceCorrispettivo() {}

  public VoceCorrispettivo(TipoVoce tipo, Fascia fascia, BigDecimal valore, boolean indicizzata) {
    this.tipo = tipo;
    this.fascia = fascia;
    this.corrispettivo = valore;
    this.indicizzata = indicizzata;
  }

  void assegnaOfferta(Offerta offerta) {
    this.offerta = offerta;
  }

  public Long getId() {
    return id;
  }

  public TipoVoce getTipo() {
    return tipo;
  }

  public Fascia getFascia() {
    return fascia;
  }

  public BigDecimal getCorrispettivo() {
    return corrispettivo;
  }

  public boolean isIndicizzata() {
    return indicizzata;
  }

  public Long getOffertaId() {
    return offerta.getId();
  }
}
