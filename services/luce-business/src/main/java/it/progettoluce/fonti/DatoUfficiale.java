package it.progettoluce.fonti;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(
    name = "dati_ufficiali",
    uniqueConstraints = @UniqueConstraint(columnNames = {"codice", "periodo", "categoria"}))
public class DatoUfficiale {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Version Long versione;

  @Column(nullable = false, length = 80)
  String codice;

  @Column(nullable = false, length = 7)
  String periodo;

  @Column(nullable = false, length = 40)
  String categoria;

  @Column(precision = 20, scale = 8)
  BigDecimal valoreUfficiale;

  @Column(precision = 20, scale = 8)
  BigDecimal valoreManuale;

  @Column(nullable = false, length = 100)
  String fonte;

  @Column(nullable = false, length = 1000)
  String url;

  LocalDate pubblicatoIl;

  @Column(nullable = false)
  Instant acquisitoIl;

  protected DatoUfficiale() {}

  DatoUfficiale(String codice, String periodo, String categoria) {
    this.codice = codice;
    this.periodo = periodo;
    this.categoria = categoria;
    fonte = "MANUALE";
    url = "";
    acquisitoIl = Instant.now();
  }

  BigDecimal effettivo() {
    return valoreManuale == null ? valoreUfficiale : valoreManuale;
  }
}
