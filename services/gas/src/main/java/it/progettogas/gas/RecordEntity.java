package it.progettogas.gas;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "gas_records")
public class RecordEntity extends it.bollettalab.platform.ScopedEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Version Long version;

  @Column(nullable = false, length = 30)
  String kind;

  @Column(nullable = false, columnDefinition = "longtext")
  String payload;

  @Column(nullable = false)
  Instant createdAt;

  protected RecordEntity() {}

  RecordEntity(String kind, String payload) {
    this.kind = kind;
    this.payload = payload;
    // Match database timestamp precision so POST and subsequent GET return identical metadata.
    this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
  }
}
