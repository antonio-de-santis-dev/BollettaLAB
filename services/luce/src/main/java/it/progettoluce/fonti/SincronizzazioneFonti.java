package it.progettoluce.fonti;

import java.time.YearMonth;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Service;

@Service
@EnableScheduling
public class SincronizzazioneFonti {
  public record Stato(
      boolean automatico,
      boolean gmeConfigurato,
      boolean inCorso,
      java.util.List<String> periodi,
      java.util.List<ArchivioFonti.Esito> esiti) {}

  private final ArchivioFonti archivio;
  private final PortaleOfferteClient portale;
  private final GmeClient gme;
  private final boolean automatico;
  private final AtomicBoolean busy = new AtomicBoolean();

  public SincronizzazioneFonti(
      ArchivioFonti archivio,
      PortaleOfferteClient portale,
      GmeClient gme,
      @Value("${fonti.automatico:true}") boolean automatico) {
    this.archivio = archivio;
    this.portale = portale;
    this.gme = gme;
    this.automatico = automatico;
  }

  public Stato stato() {
    return new Stato(
        automatico, gme.configurato(), busy.get(), archivio.periodi(), archivio.esiti());
  }

  @Scheduled(
      initialDelayString = "${fonti.avvio-ms:30000}",
      fixedDelayString = "${fonti.intervallo-ms:86400000}")
  public void pianificata() {
    if (automatico) sincronizza(null);
  }

  public Stato sincronizza(String periodoGme) {
    YearMonth mese =
        periodoGme == null
            ? YearMonth.now(java.time.ZoneId.of("Europe/Rome")).minusMonths(1)
            : YearMonth.parse(periodoGme);
    if (!mese.isBefore(YearMonth.now(java.time.ZoneId.of("Europe/Rome"))))
      throw new IllegalArgumentException("GME richiede un mese concluso");
    if (!busy.compareAndSet(false, true)) return stato();
    try {
      try {
        int n = archivio.importa(portale.scarica());
        archivio.esito(
            "PORTALE_OFFERTE",
            true,
            n + " valori nuovi o aggiornati. Correzioni manuali conservate.");
      } catch (Exception e) {
        archivio.esito(
            "PORTALE_OFFERTE",
            false,
            "Importazione non riuscita: fonte non raggiungibile o formato non riconosciuto. Dati"
                + " precedenti conservati.");
      }
      if (gme.configurato()) {
        try {
          int n = archivio.importa(gme.scarica(mese));
          archivio.esito("GME", true, mese + ": " + n + " indici nuovi o aggiornati.");
        } catch (Exception e) {
          archivio.esito(
              "GME",
              false,
              mese
                  + ": importazione non riuscita. Verifica credenziali, disponibilità e formato dei"
                  + " dati. Dati precedenti conservati.");
        }
      }
    } finally {
      busy.set(false);
    }
    return stato();
  }
}
