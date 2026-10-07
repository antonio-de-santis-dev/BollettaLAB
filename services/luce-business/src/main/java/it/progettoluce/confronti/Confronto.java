package it.progettoluce.confronti;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "confronti")
public class Confronto extends it.bollettalab.platform.ScopedEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Instant creatoIl;

  @Column(nullable = false, columnDefinition = "longtext")
  private String snapshot;

  protected Confronto() {}

  public Confronto(String snapshot) {
    this.snapshot = snapshot;
    creatoIl = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public Instant getCreatoIl() {
    return creatoIl;
  }

  public String getSnapshot() {
    return snapshot;
  }
}
