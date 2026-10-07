package it.progettoluce.parametri;

import it.progettoluce.shared.JsonCodec;
import it.progettoluce.shared.NotFoundException;
import java.util.Objects;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ParametriService {
  private final ParametriRepository repository;
  private final JsonCodec codec;

  public ParametriService(ParametriRepository repository, JsonCodec codec) {
    this.repository = repository;
    this.codec = codec;
  }

  @Transactional(readOnly = true)
  public ParametriRequest leggi() {
    var p =
        repository.findAll().stream()
            .findFirst()
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "Configura e salva i parametri del gestore prima del confronto"));
    var r = codec.leggi(p.getConfigurazione(), ParametriRequest.class);
    return new ParametriRequest(
        r.nomeProfilo(),
        r.fonte(),
        r.coefficientePerdite(),
        r.arrotondaPerdite(),
        r.dispacciamentoKwh(),
        r.trasportoFissoMese(),
        r.trasportoPotenzaAnno(),
        r.trasportoKwh(),
        r.oneriFissiMese(),
        r.oneriKwh(),
        r.accisaKwh(),
        p.getVersione(),
        r.oneriPotenzaMese(),
        r.oneriSuPerdite());
  }

  @Transactional
  public ParametriRequest salva(ParametriRequest r) {
    var esistente = repository.findAll().stream().findFirst();
    if (esistente.isPresent()) {
      var p = esistente.get();
      if (!Objects.equals(p.getVersione(), r.versione()))
        throw new ObjectOptimisticLockingFailureException(ParametriGestore.class, 1L);
      p.aggiorna(codec.scrivi(r));
      repository.flush();
    } else {
      if (r.versione() != null)
        throw new ObjectOptimisticLockingFailureException(ParametriGestore.class, 1L);
      repository.saveAndFlush(new ParametriGestore(codec.scrivi(r)));
    }
    return leggi();
  }
}
