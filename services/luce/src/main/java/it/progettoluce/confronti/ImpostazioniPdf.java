package it.progettoluce.confronti;

import jakarta.persistence.*;

@Entity
@Table(name = "impostazioni_pdf")
public class ImpostazioniPdf extends it.bollettalab.platform.ScopedEntity {
  @Id
  @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
  private Long id;

  @Version private Long versione;

  @Column(nullable = false, columnDefinition = "longtext")
  private String configurazione;

  protected ImpostazioniPdf() {}

  public ImpostazioniPdf(String c) {
    this.configurazione = c;
  }

  public Long getVersione() {
    return versione;
  }

  public String getConfigurazione() {
    return configurazione;
  }

  public void aggiorna(String value) {
    configurazione = value;
  }
}
