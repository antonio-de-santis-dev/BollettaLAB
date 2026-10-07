package it.progettoluce.offerte;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "offerte")
public class Offerta extends it.bollettalab.platform.ScopedEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version private Long versione;

  @Column(nullable = false, length = 150)
  private String nomeFornitore;

  @Column(nullable = false, length = 150)
  private String nomeOfferta;

  @Enumerated(EnumType.STRING)
  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
  @Column(nullable = false)
  private TipoOfferta tipoOfferta;

  @Enumerated(EnumType.STRING)
  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
  @Column(nullable = false)
  private TipoTariffa tipoTariffa;

  @Embedded
  @AttributeOverrides({
    @AttributeOverride(
        name = "f0",
        column = @Column(name = "prezzo_f0", precision = 16, scale = 8)),
    @AttributeOverride(
        name = "f1",
        column = @Column(name = "prezzo_f1", precision = 16, scale = 8)),
    @AttributeOverride(
        name = "f23",
        column = @Column(name = "prezzo_f23", precision = 16, scale = 8)),
    @AttributeOverride(
        name = "f2",
        column = @Column(name = "prezzo_f2", precision = 16, scale = 8)),
    @AttributeOverride(name = "f3", column = @Column(name = "prezzo_f3", precision = 16, scale = 8))
  })
  private Prezzi prezzi;

  @Embedded
  @AttributeOverrides({
    @AttributeOverride(
        name = "f0",
        column = @Column(name = "spread_f0", precision = 16, scale = 8)),
    @AttributeOverride(
        name = "f1",
        column = @Column(name = "spread_f1", precision = 16, scale = 8)),
    @AttributeOverride(
        name = "f23",
        column = @Column(name = "spread_f23", precision = 16, scale = 8)),
    @AttributeOverride(
        name = "f2",
        column = @Column(name = "spread_f2", precision = 16, scale = 8)),
    @AttributeOverride(name = "f3", column = @Column(name = "spread_f3", precision = 16, scale = 8))
  })
  private Prezzi spread;

  @Column(nullable = false, precision = 16, scale = 8)
  private BigDecimal pcvAnnuo;

  @Column(nullable = false)
  private boolean attiva;

  @Column(length = 2000)
  private String note;

  @OneToMany(mappedBy = "offerta", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("id ASC")
  private List<VoceCorrispettivo> voci = new ArrayList<>();

  public Offerta() {}

  public void aggiorna(OffertaRequest r, List<VoceCorrispettivo> nuoveVoci) {
    nomeFornitore = r.nomeFornitore().trim();
    nomeOfferta = r.nomeOfferta().trim();
    tipoOfferta = r.tipoOfferta();
    tipoTariffa = r.tipoTariffa();
    prezzi = tipoOfferta == TipoOfferta.PREZZO_FISSO ? r.prezzi() : null;
    spread = tipoOfferta == TipoOfferta.INDICIZZATA_PUN ? r.spread() : null;
    pcvAnnuo = r.pcvAnnuo();
    attiva = r.attiva();
    note = r.note();
    voci.forEach(v -> v.assegnaOfferta(null));
    voci.clear();
    nuoveVoci.forEach(
        v -> {
          v.assegnaOfferta(this);
          voci.add(v);
        });
  }

  public Long getId() {
    return id;
  }

  public Long getVersione() {
    return versione;
  }

  public String getNomeFornitore() {
    return nomeFornitore;
  }

  public String getNomeOfferta() {
    return nomeOfferta;
  }

  public TipoOfferta getTipoOfferta() {
    return tipoOfferta;
  }

  public TipoTariffa getTipoTariffa() {
    return tipoTariffa;
  }

  public Prezzi getPrezzi() {
    return prezzi;
  }

  public Prezzi getSpread() {
    return spread;
  }

  public BigDecimal getPcvAnnuo() {
    return pcvAnnuo;
  }

  public boolean isAttiva() {
    return attiva;
  }

  public String getNote() {
    return note;
  }

  public List<VoceCorrispettivo> getVoci() {
    return List.copyOf(voci);
  }
}
