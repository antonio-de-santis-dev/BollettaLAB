package it.progettoluce.offerte;

import static it.progettoluce.offerte.VoceCorrispettivo.TipoVoce.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OffertaVociFactory {
  public List<VoceCorrispettivo> crea(OffertaRequest r) {
    if (r.tipoOfferta() == null || r.tipoTariffa() == null)
      throw new IllegalArgumentException("Tipo offerta e tariffa obbligatori");
    Prezzi valori = r.tipoOfferta() == TipoOfferta.PREZZO_FISSO ? r.prezzi() : r.spread();
    if (valori == null)
      throw new IllegalArgumentException(
          "Inserisci i prezzi o gli spread della tariffa selezionata");
    List<Fascia> fasce =
        switch (r.tipoTariffa()) {
          case MONORARIA -> List.of(Fascia.F0);
          case BIORARIA -> List.of(Fascia.F1, Fascia.F23);
          case TRIORARIA -> List.of(Fascia.F1, Fascia.F2, Fascia.F3);
        };
    List<VoceCorrispettivo> voci = new ArrayList<>();
    for (Fascia fascia : fasce) {
      BigDecimal valore = valori.valore(fascia);
      if (valore == null || valore.signum() < 0)
        throw new IllegalArgumentException("Valore non negativo obbligatorio per " + fascia);
      voci.add(
          new VoceCorrispettivo(
              ENERGIA, fascia, valore, r.tipoOfferta() == TipoOfferta.INDICIZZATA_PUN));
    }
    if (r.pcvAnnuo() == null || r.pcvAnnuo().signum() < 0)
      throw new IllegalArgumentException("PCV annuo non negativo obbligatorio");
    // Persist the annual rate: dividing by twelve here would lose precision before multiplication.
    voci.add(new VoceCorrispettivo(PCV, null, r.pcvAnnuo(), false));
    return voci;
  }
}
