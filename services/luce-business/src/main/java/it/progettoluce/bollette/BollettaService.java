package it.progettoluce.bollette;

import it.progettoluce.shared.NotFoundException;
import java.util.*;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BollettaService {
  public record Risposta(Long id, BollettaRequest dati) {}

  private final BollettaRepository repository;

  public BollettaService(BollettaRepository repository) {
    this.repository = repository;
  }

  public BollettaConcorrente trova(Long id) {
    return repository.findById(id).orElseThrow(() -> new NotFoundException("Bolletta non trovata"));
  }

  @Transactional(readOnly = true)
  public List<Risposta> lista() {
    return repository.findAll(org.springframework.data.domain.Sort.by("id").descending()).stream()
        .map(this::risposta)
        .toList();
  }

  @Transactional(readOnly = true)
  public Risposta leggi(Long id) {
    return risposta(trova(id));
  }

  @Transactional
  public Risposta crea(BollettaRequest r) {
    var b = new BollettaConcorrente();
    b.aggiorna(r);
    return risposta(repository.saveAndFlush(b));
  }

  @Transactional
  public Risposta aggiorna(Long id, BollettaRequest r) {
    var b = trova(id);
    if (r.versione() == null)
      throw new IllegalArgumentException("Versione obbligatoria per aggiornare la bolletta");
    if (!Objects.equals(b.getVersione(), r.versione()))
      throw new ObjectOptimisticLockingFailureException(BollettaConcorrente.class, id);
    b.aggiorna(r);
    repository.flush();
    return risposta(b);
  }

  @Transactional
  public void elimina(Long id) {
    repository.delete(trova(id));
  }

  private Risposta risposta(BollettaConcorrente b) {
    return new Risposta(b.getId(), b.dati());
  }
}
