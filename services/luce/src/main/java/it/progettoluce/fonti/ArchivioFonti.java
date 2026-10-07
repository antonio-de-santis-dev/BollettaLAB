package it.progettoluce.fonti;

import it.progettoluce.shared.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArchivioFonti {
  public record Importato(
      String codice,
      String periodo,
      String categoria,
      BigDecimal valore,
      String fonte,
      String url,
      LocalDate pubblicatoIl) {}

  public record Dato(
      Long id,
      Long versione,
      String codice,
      String periodo,
      String categoria,
      String unita,
      BigDecimal valore,
      BigDecimal valoreUfficiale,
      BigDecimal valoreManuale,
      String fonte,
      String url,
      LocalDate pubblicatoIl,
      Instant acquisitoIl) {}

  public record Revisione(Long id, Instant creatoIl, String motivo, String contenuto) {}

  public record Esito(Instant creatoIl, String fonte, boolean successo, String messaggio) {}

  private final DatoUfficialeRepository repository;
  private final JdbcTemplate jdbc;
  private final JsonCodec codec;

  public ArchivioFonti(DatoUfficialeRepository repository, JdbcTemplate jdbc, JsonCodec codec) {
    this.repository = repository;
    this.jdbc = jdbc;
    this.codec = codec;
  }

  Dato dto(DatoUfficiale d) {
    return new Dato(
        d.id,
        d.versione,
        d.codice,
        d.periodo,
        d.categoria,
        CatalogoFonti.UNITA.getOrDefault(d.codice, "come pubblicato"),
        d.effettivo(),
        d.valoreUfficiale,
        d.valoreManuale,
        d.fonte,
        d.url,
        d.pubblicatoIl,
        d.acquisitoIl);
  }

  @Transactional(readOnly = true)
  public List<Dato> lista(String periodo, String categoria) {
    return repository.findByPeriodoAndCategoriaOrderByCodice(periodo, categoria).stream()
        .map(this::dto)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<String> periodi() {
    return jdbc.queryForList(
        "SELECT DISTINCT periodo FROM dati_ufficiali ORDER BY periodo DESC", String.class);
  }

  @Transactional(readOnly = true)
  public List<Revisione> revisioni(Long id) {
    return jdbc.query(
        "SELECT id,creato_il,motivo,contenuto FROM revisioni_fonti WHERE dato_id=? ORDER BY id DESC"
            + " LIMIT 100",
        (r, n) ->
            new Revisione(
                r.getLong(1), r.getTimestamp(2).toInstant(), r.getString(3), r.getString(4)),
        id);
  }

  @Transactional(readOnly = true)
  public List<Esito> esiti() {
    return jdbc.query(
        "SELECT creato_il,fonte,successo,messaggio FROM sincronizzazioni_fonti ORDER BY id DESC"
            + " LIMIT 20",
        (r, n) ->
            new Esito(
                r.getTimestamp(1).toInstant(), r.getString(2), r.getBoolean(3), r.getString(4)));
  }

  @Transactional
  public int importa(List<Importato> dati) {
    int modificati = 0;
    for (var r : dati) {
      if (r.categoria().equals(CatalogoFonti.ORIGINALE)) {
        if (r.valore().abs().compareTo(new BigDecimal("999999999999")) >= 0
            || r.valore().scale() > 8) throw new IllegalArgumentException("Dato fuori intervallo");
      } else CatalogoFonti.valida(r.codice(), r.periodo(), r.categoria(), r.valore());
      var d =
          repository
              .findByCodiceAndPeriodoAndCategoria(r.codice(), r.periodo(), r.categoria())
              .orElseGet(() -> new DatoUfficiale(r.codice(), r.periodo(), r.categoria()));
      // An enabled GME feed takes precedence over the rounded F0 history supplied by AU.
      if (d.fonte.equals("GME")
          && r.fonte().equals("PORTALE_OFFERTE")
          && r.codice().startsWith("PUN_")) continue;
      boolean cambiato =
          d.valoreUfficiale == null
              || d.valoreUfficiale.compareTo(r.valore()) != 0
              || !d.fonte.equals(r.fonte())
              || !d.url.equals(r.url());
      d.valoreUfficiale = r.valore();
      d.fonte = r.fonte();
      d.url = r.url();
      d.pubblicatoIl = r.pubblicatoIl();
      d.acquisitoIl = Instant.now();
      repository.saveAndFlush(d);
      if (cambiato) {
        storico(d, "Importazione ufficiale");
        modificati++;
      }
    }
    return modificati;
  }

  @Transactional
  public Dato manuale(
      String codice,
      String periodo,
      String categoria,
      BigDecimal valore,
      Long versione,
      String motivo) {
    CatalogoFonti.valida(codice, periodo, categoria, valore);
    var d =
        repository
            .findByCodiceAndPeriodoAndCategoria(codice, periodo, categoria)
            .orElseGet(() -> new DatoUfficiale(codice, periodo, categoria));
    verificaVersione(d, versione);
    d.valoreManuale = valore;
    repository.saveAndFlush(d);
    storico(d, motivo);
    return dto(d);
  }

  @Transactional
  public Dato ripristina(Long id, Long versione, String motivo) {
    var d = repository.findById(id).orElseThrow(() -> new NotFoundException("Dato non trovato"));
    verificaVersione(d, versione);
    if (d.valoreUfficiale == null)
      throw new IllegalArgumentException(
          "Nessun valore ufficiale disponibile: conserva o modifica il dato manuale");
    d.valoreManuale = null;
    repository.flush();
    storico(d, motivo);
    return dto(d);
  }

  private void verificaVersione(DatoUfficiale d, Long versione) {
    if (!Objects.equals(d.versione, versione))
      throw new ObjectOptimisticLockingFailureException(DatoUfficiale.class, d.id);
  }

  private void storico(DatoUfficiale d, String motivo) {
    jdbc.update(
        "INSERT INTO revisioni_fonti(dato_id,creato_il,motivo,contenuto) VALUES (?,?,?,?)",
        d.id,
        java.sql.Timestamp.from(Instant.now()),
        motivo,
        codec.scrivi(dto(d)));
  }

  @Transactional
  public void esito(String fonte, boolean successo, String messaggio) {
    jdbc.update(
        "INSERT INTO sincronizzazioni_fonti(creato_il,fonte,successo,messaggio) VALUES(?,?,?,?)",
        java.sql.Timestamp.from(Instant.now()),
        fonte,
        successo,
        messaggio);
    jdbc.update(
        "DELETE FROM sincronizzazioni_fonti WHERE id NOT IN (SELECT id FROM (SELECT id FROM"
            + " sincronizzazioni_fonti ORDER BY id DESC LIMIT 100) recenti)");
  }
}
