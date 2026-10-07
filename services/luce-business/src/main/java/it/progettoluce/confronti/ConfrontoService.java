package it.progettoluce.confronti;

import it.progettoluce.bollette.*;
import it.progettoluce.calcolo.*;
import it.progettoluce.fonti.ParametriUfficiali;
import it.progettoluce.offerte.*;
import it.progettoluce.parametri.*;
import it.progettoluce.shared.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfrontoService {
  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private it.bollettalab.platform.LocalReceipts receipts;

  public record Richiesta(
      @NotNull @Positive Long bollettaId,
      @NotNull @Positive Long offertaId,
      boolean usaFontiUfficiali,
      String categoria,
      boolean confermaStandard) {
    public Richiesta(Long bollettaId, Long offertaId) {
      this(bollettaId, offertaId, false, null, false);
    }
  }

  public record Snapshot(
      String versioneMotore,
      Long bollettaId,
      BollettaRequest bolletta,
      OffertaResponse offerta,
      ParametriRequest parametri,
      RisultatoCalcolo risultato,
      List<ParametriUfficiali.Mensile> parametriMensili) {
    public Snapshot(
        String versioneMotore,
        Long bollettaId,
        BollettaRequest bolletta,
        OffertaResponse offerta,
        ParametriRequest parametri,
        RisultatoCalcolo risultato) {
      this(versioneMotore, bollettaId, bolletta, offerta, parametri, risultato, null);
    }
  }

  public record Risposta(Long id, Instant creatoIl, Snapshot dati) {}

  private final BollettaService bollette;
  private final OffertaService offerte;
  private final ParametriService parametri;
  private final MotoreCalcolo motore;
  private final ConfrontoRepository repository;
  private final JsonCodec codec;
  private final ParametriUfficiali ufficiali;

  @org.springframework.beans.factory.annotation.Autowired
  private org.springframework.core.env.Environment environment;

  public ConfrontoService(
      BollettaService bollette,
      OffertaService offerte,
      ParametriService parametri,
      MotoreCalcolo motore,
      ConfrontoRepository repository,
      JsonCodec codec,
      ParametriUfficiali ufficiali) {
    this.bollette = bollette;
    this.offerte = offerte;
    this.parametri = parametri;
    this.motore = motore;
    this.repository = repository;
    this.codec = codec;
    this.ufficiali = ufficiali;
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public Risposta crea(Richiesta r) {
    var b = bollette.trova(r.bollettaId());
    var o = offerte.trova(r.offertaId());
    var p = parametri.leggi();
    Snapshot snapshot;
    if (r.usaFontiUfficiali()) {
      if (!environment.acceptsProfiles(org.springframework.core.env.Profiles.of("domestico")))
        throw new IllegalArgumentException(
            "Per il business configura i parametri della fornitura: i profili ufficiali domestici"
                + " non sono applicabili.");
      if (!r.confermaStandard())
        throw new IllegalArgumentException(
            "Conferma la compatibilità del dispacciamento standard con l'offerta e verifica"
                + " l'assenza di doppie componenti");
      var preparato = ufficiali.prepara(b.dati(), o, r.categoria());
      var profili =
          preparato.mesi().stream()
              .collect(
                  java.util.stream.Collectors.toMap(
                      ParametriUfficiali.Mensile::mese, ParametriUfficiali.Mensile::parametri));
      snapshot =
          new Snapshot(
              "1.1",
              b.getId(),
              preparato.bolletta().dati(),
              OffertaResponse.da(o),
              preparato.mesi().get(0).parametri(),
              motore.calcola(preparato.bolletta(), o, profili),
              preparato.mesi());
    } else
      snapshot =
          new Snapshot(
              "1.0", b.getId(), b.dati(), OffertaResponse.da(o), p, motore.calcola(b, o, p));
    var saved = repository.saveAndFlush(new Confronto(codec.scrivi(snapshot)));
    var result = risposta(saved);
    if (receipts != null) receipts.record(saved.getId(), result);
    return result;
  }

  @Transactional(readOnly = true)
  public List<Risposta> lista() {
    return repository.findAll(org.springframework.data.domain.Sort.by("id").descending()).stream()
        .map(this::risposta)
        .toList();
  }

  @Transactional(readOnly = true)
  public Risposta leggi(Long id) {
    return risposta(
        repository.findById(id).orElseThrow(() -> new NotFoundException("Confronto non trovato")));
  }

  private Risposta risposta(Confronto c) {
    return new Risposta(c.getId(), c.getCreatoIl(), codec.leggi(c.getSnapshot(), Snapshot.class));
  }
}
