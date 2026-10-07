package it.progettoluce.business;

import static it.progettoluce.business.BusinessModels.*;

import it.progettoluce.shared.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessService {
  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private it.bollettalab.platform.LocalReceipts receipts;

  private final BusinessProfileRepository profili;
  private final BusinessSimulationRepository simulazioni;
  private final BusinessEngine motore;
  private final JsonCodec codec;
  private final JdbcTemplate jdbc;

  public BusinessService(
      BusinessProfileRepository p,
      BusinessSimulationRepository s,
      BusinessEngine m,
      JsonCodec c,
      JdbcTemplate j) {
    profili = p;
    simulazioni = s;
    motore = m;
    codec = c;
    jdbc = j;
  }

  @Transactional(readOnly = true)
  public List<Profilo> profili() {
    return profili.findAll(Sort.by("id").descending()).stream().map(this::dto).toList();
  }

  @Transactional
  public Profilo salva(Long id, ProfiloInput r) {
    validaProfilo(r);
    BusinessProfile p;
    if (id == null) {
      if (r.versione() != null)
        throw new IllegalArgumentException("Non inviare una versione nella creazione.");
      p = new BusinessProfile(codec.scrivi(r));
    } else {
      p =
          profili
              .findById(id)
              .orElseThrow(() -> new NotFoundException("Profilo business non trovato"));
      if (r.versione() == null || !Objects.equals(p.versione, r.versione()))
        throw new ObjectOptimisticLockingFailureException(BusinessProfile.class, id);
      p.configurazione = codec.scrivi(r);
    }
    profili.saveAndFlush(p);
    var risposta = dto(p);
    jdbc.update(
        "INSERT INTO business_revisioni_profili(profilo_id,versione,modificato_il,configurazione)"
            + " VALUES (?,?,?,?)",
        p.id,
        p.versione,
        Timestamp.from(Instant.now()),
        codec.scrivi(risposta));
    return risposta;
  }

  private void validaProfilo(ProfiloInput p) {
    YearMonth.parse(p.dal());
    if (p.al() != null && (YearMonth.parse(p.al()).isBefore(YearMonth.parse(p.dal()))))
      throw new IllegalArgumentException("La scadenza precede la decorrenza.");
    if (p.verificato() && (p.notaVerifica() == null || p.notaVerifica().isBlank()))
      throw new IllegalArgumentException(
          "Indica motivo, fonte e responsabile della verifica del profilo.");
    Set<String> codici = new HashSet<>();
    for (var v : p.voci()) {
      if (!codici.add(v.codice()))
        throw new IllegalArgumentException("Codice voce duplicato: " + v.codice());
      boolean fascia = v.base() == Base.KWH_FASCIA || v.base() == Base.PERDITE_FASCIA;
      if (fascia && v.fascia() == null)
        throw new IllegalArgumentException("Seleziona la fascia per " + v.descrizione());
      if (!fascia && v.fascia() != null)
        throw new IllegalArgumentException(
            "La base della voce " + v.descrizione() + " non richiede una fascia.");
      if (v.indicizzata()
          && (!fascia && v.base() != Base.KWH_NETTI && v.base() != Base.KWH_CON_PERDITE))
        throw new IllegalArgumentException("Solo le voci energia possono usare PUN + spread.");
      if (v.indicizzata() && v.fascia() == null)
        throw new IllegalArgumentException(
            "Per PUN scegli una base per fascia (F0 per il totale).");
    }
  }

  @Transactional(readOnly = true)
  public List<Profilo> revisioni(Long id) {
    if (!profili.existsById(id)) throw new NotFoundException("Profilo non trovato");
    return jdbc.query(
        "SELECT configurazione FROM business_revisioni_profili WHERE profilo_id=? ORDER BY versione"
            + " DESC",
        (r, n) -> codec.leggi(r.getString(1), Profilo.class),
        id);
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public Simulazione crea(SimulazioneInput input) {
    Map<Long, Profilo> scelti = new HashMap<>();
    for (var m : input.mesi())
      if (!scelti.containsKey(m.profiloId()))
        scelti.put(
            m.profiloId(),
            dto(
                profili
                    .findById(m.profiloId())
                    .orElseThrow(() -> new NotFoundException("Profilo business non trovato"))));
    var s = new Snapshot("business-1.0", input, motore.calcola(input, scelti));
    var entity = simulazioni.saveAndFlush(new BusinessSimulation(s, codec.scrivi(s)));
    var result = new Simulazione(entity.id, entity.creataIl, s);
    if (receipts != null) receipts.record(entity.id, result);
    return result;
  }

  @Transactional(readOnly = true)
  public Pagina<Riepilogo> storico(int pagina, int dimensione) {
    var page = simulazioni.findAll(PageRequest.of(pagina, dimensione, Sort.by("id").descending()));
    return new Pagina<>(
        page.stream()
            .map(
                e ->
                    new Riepilogo(
                        e.id,
                        e.creataIl,
                        e.ragioneSociale,
                        e.dal,
                        e.al,
                        e.totale,
                        e.risparmio,
                        e.bozza))
            .toList(),
        page.getTotalElements(),
        pagina,
        dimensione);
  }

  @Transactional(readOnly = true)
  public Simulazione leggi(Long id) {
    var e =
        simulazioni
            .findById(id)
            .orElseThrow(() -> new NotFoundException("Simulazione non trovata"));
    return new Simulazione(e.id, e.creataIl, codec.leggi(e.snapshot, Snapshot.class));
  }

  private Profilo dto(BusinessProfile p) {
    return new Profilo(p.id, p.versione, codec.leggi(p.configurazione, ProfiloInput.class));
  }
}
