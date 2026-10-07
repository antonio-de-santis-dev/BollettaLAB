package it.progettoluce.offerte;

import it.progettoluce.shared.NotFoundException;
import java.util.List;
import java.util.Objects;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OffertaService {
  private final OffertaRepository repository;
  private final OffertaVociFactory factory;

  public OffertaService(OffertaRepository repository, OffertaVociFactory factory) {
    this.repository = repository;
    this.factory = factory;
  }

  @Transactional(readOnly = true)
  public List<OffertaResponse> lista() {
    return repository.findAll(org.springframework.data.domain.Sort.by("id").descending()).stream()
        .map(OffertaResponse::da)
        .toList();
  }

  @Transactional(readOnly = true)
  public OffertaResponse leggi(Long id) {
    return OffertaResponse.da(trova(id));
  }

  public Offerta trova(Long id) {
    return repository.findById(id).orElseThrow(() -> new NotFoundException("Offerta non trovata"));
  }

  @Transactional
  public OffertaResponse crea(OffertaRequest r) {
    Offerta o = new Offerta();
    o.aggiorna(r, factory.crea(r));
    return OffertaResponse.da(repository.saveAndFlush(o));
  }

  @Transactional
  public OffertaResponse aggiorna(Long id, OffertaRequest r) {
    Offerta o = trova(id);
    if (r.versione() == null)
      throw new IllegalArgumentException("Versione obbligatoria per aggiornare l'offerta");
    if (!Objects.equals(o.getVersione(), r.versione()))
      throw new ObjectOptimisticLockingFailureException(Offerta.class, id);
    o.aggiorna(r, factory.crea(r));
    return OffertaResponse.da(repository.saveAndFlush(o));
  }

  @Transactional
  public void elimina(Long id) {
    repository.delete(trova(id));
  }
}
