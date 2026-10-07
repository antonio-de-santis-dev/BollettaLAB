package it.progettoluce.confronti;

import it.progettoluce.bollette.*;
import it.progettoluce.calcolo.RisultatoCalcolo;
import it.progettoluce.offerte.*;
import it.progettoluce.parametri.ParametriRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Unsaved synthetic data solely for configuring the report before a consultation exists. */
final class PdfEsempio {
  private PdfEsempio() {}

  static ConfrontoService.Risposta crea() {
    String cliente = "Mario Rossi · Cliente dimostrativo",
        note = "Esempio grafico: dati sintetici per l'anteprima.";
    int count = 10;
    var z = BigDecimal.ZERO;
    var mesi = List.of(new MeseRequest("2026-01", b("100"), b("200"), b("300"), null));
    var bolletta =
        new BollettaRequest(
            cliente, "POD-DEMO", "Fornitore Demo", b("3"), b("200"), b("0.1"), z, z, mesi, 0L);
    var offerta =
        new OffertaResponse(
            1L,
            0L,
            "Gestore Demo",
            "Luce Fissa Test",
            TipoOfferta.PREZZO_FISSO,
            TipoTariffa.MONORARIA,
            new Prezzi(b("0.1"), null, null, null, null),
            null,
            b("120"),
            true,
            note,
            List.of(new OffertaResponse.VoceDTO(1L, "ENERGIA", Fascia.F0, b("0.1"), false)));
    var parametri =
        new ParametriRequest(
            "Profilo dimostrativo",
            "Dati sintetici di esempio, gennaio 2026",
            z,
            false,
            z,
            z,
            z,
            z,
            z,
            z,
            z,
            0L);
    var righe = new ArrayList<RisultatoCalcolo.Riga>();
    String[] descrizioni = {
      "Energia F0",
      "Commercializzazione (PCV)",
      "Dispacciamento",
      "Trasporto · quota fissa",
      "Trasporto · potenza",
      "Trasporto · energia",
      "Oneri · quota fissa",
      "Oneri · energia",
      "Accisa configurata",
      "Altre partite imponibili"
    };
    for (int i = 0; i < count; i++) {
      righe.add(
          new RisultatoCalcolo.Riga(
              "2026-01",
              descrizioni[i],
              i < 3
                  ? "ENERGIA"
                  : i < 6 ? "TRASPORTO" : i < 8 ? "ONERI" : i == 8 ? "IMPOSTE" : "ALTRE_PARTITE",
              i == 4 ? "€/kW/mese" : i == 9 ? "€" : i == 1 || i == 3 || i == 6 ? "€/mese" : "€/kWh",
              i == 4 ? b("3") : i == 1 || i == 3 || i == 6 || i == 9 ? b("1") : b("600"),
              i == 0 ? b("0.1") : i == 1 ? b("10") : z,
              i == 0 ? b("60") : i == 1 ? b("10") : z));
    }
    var categorie = new LinkedHashMap<String, BigDecimal>();
    categorie.put("ENERGIA", b("70"));
    categorie.put("TRASPORTO", z);
    categorie.put("ONERI", z);
    categorie.put("IMPOSTE", z);
    categorie.put("ALTRE_PARTITE", z);
    var risultato =
        new RisultatoCalcolo(
            righe,
            categorie,
            b("70"),
            b("7"),
            z,
            b("77"),
            b("123"),
            b("61.5"),
            b("1476"),
            1,
            "Proiezione indicativa: risparmio periodo × 12 / mesi. Non considera stagionalità o"
                + " variazioni future del PUN.");
    return new ConfrontoService.Risposta(
        0L,
        Instant.parse("2026-01-10T10:00:00Z"),
        new ConfrontoService.Snapshot("1.0", 1L, bolletta, offerta, parametri, risultato));
  }

  private static BigDecimal b(String value) {
    return new BigDecimal(value);
  }
}
