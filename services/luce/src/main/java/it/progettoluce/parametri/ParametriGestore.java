package it.progettoluce.parametri;

import jakarta.persistence.*;

@Entity
@Table(name = "parametri_gestore")
public class ParametriGestore extends it.bollettalab.platform.ScopedEntity {
  @Id
  @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
  private Long id;

  @Version private Long versione;

  @Column(nullable = false, columnDefinition = "longtext")
  private String configurazione;

  protected ParametriGestore() {}

  public ParametriGestore(String configurazione) {

    this.configurazione = configurazione;
  }

  public Long getVersione() {
    return versione;
  }

  public String getConfigurazione() {
    return configurazione;
  }

  public void aggiorna(String configurazione) {
    this.configurazione = configurazione;
  }
}
