package it.progettoluce.confronti;

import it.progettoluce.shared.JsonCodec;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Objects;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImpostazioniPdfService {
  public record Configurazione(
      @NotNull @Valid PdfPersonalizzazione opzioni, @NotNull @PositiveOrZero Long versione) {}

  private final ImpostazioniPdfRepository repository;
  private final JsonCodec codec;

  public ImpostazioniPdfService(ImpostazioniPdfRepository repository, JsonCodec codec) {
    this.repository = repository;
    this.codec = codec;
  }

  @Transactional
  public Configurazione leggi() {
    var value =
        repository.findAll().stream()
            .findFirst()
            .orElseGet(
                () ->
                    repository.saveAndFlush(
                        new ImpostazioniPdf(codec.scrivi(PdfPersonalizzazione.predefinita()))));
    return new Configurazione(
        codec.leggi(value.getConfigurazione(), PdfPersonalizzazione.class), value.getVersione());
  }

  @Transactional
  public Configurazione salva(Configurazione richiesta) {
    PdfLogo.leggi(richiesta.opzioni().logo());
    var value =
        repository.findAll().stream()
            .findFirst()
            .orElseGet(
                () ->
                    repository.saveAndFlush(
                        new ImpostazioniPdf(codec.scrivi(PdfPersonalizzazione.predefinita()))));
    if (!Objects.equals(value.getVersione(), richiesta.versione()))
      throw new ObjectOptimisticLockingFailureException(ImpostazioniPdf.class, 1L);
    var raw = richiesta.opzioni();
    var c = raw.consulente();
    if (c == null) c = new PdfPersonalizzazione.Consulente("", "", "", "", "", false);
    else
      c =
          new PdfPersonalizzazione.Consulente(
              testo(c.nome()),
              testo(c.ruolo()),
              testo(c.email()),
              testo(c.telefono()),
              testo(c.indirizzo()),
              c.dimostrativo());
    var opzioni =
        new PdfPersonalizzazione(
            raw.stileEffettivo(),
            raw.coloreEffettivo().toUpperCase(java.util.Locale.ROOT),
            raw.logo(),
            c);
    value.aggiorna(codec.scrivi(opzioni));
    repository.flush();
    return leggi();
  }

  private static String testo(String value) {
    return value == null ? "" : value;
  }
}
