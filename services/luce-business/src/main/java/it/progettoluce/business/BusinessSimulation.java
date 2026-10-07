package it.progettoluce.business;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "business_simulazioni")
public class BusinessSimulation extends it.bollettalab.platform.ScopedEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(nullable = false)
  Instant creataIl;

  @Column(nullable = false, length = 150)
  String ragioneSociale;

  @Column(nullable = false, length = 7)
  String dal;

  @Column(nullable = false, length = 7)
  String al;

  @Column(nullable = false, precision = 30, scale = 2)
  BigDecimal totale;

  @Column(nullable = false, precision = 30, scale = 2)
  BigDecimal risparmio;

  @Column(nullable = false)
  boolean bozza;

  @Column(nullable = false, columnDefinition = "longtext")
  String snapshot;

  protected BusinessSimulation() {}

  BusinessSimulation(BusinessModels.Snapshot s, String json) {
    creataIl = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    ragioneSociale = s.input().ragioneSociale().trim();
    var mesi = s.risultato().mesi();
    dal = mesi.get(0).mese();
    al = mesi.get(mesi.size() - 1).mese();
    totale = s.risultato().totale();
    risparmio = s.risultato().risparmioPeriodo();
    bozza = mesi.stream().anyMatch(m -> !m.profilo().dati().verificato());
    snapshot = json;
  }
}
