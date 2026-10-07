package it.progettoluce.business;

import jakarta.persistence.*;

@Entity
@Table(name = "business_profili")
public class BusinessProfile extends it.bollettalab.platform.ScopedEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Version Long versione;

  @Column(nullable = false, columnDefinition = "longtext")
  String configurazione;

  protected BusinessProfile() {}

  BusinessProfile(String json) {
    configurazione = json;
  }
}
