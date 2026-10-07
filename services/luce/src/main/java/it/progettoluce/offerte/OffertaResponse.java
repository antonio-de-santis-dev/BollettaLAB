package it.progettoluce.offerte;

import java.math.BigDecimal;
import java.util.List;

public record OffertaResponse(
    Long id,
    Long versione,
    String nomeFornitore,
    String nomeOfferta,
    TipoOfferta tipoOfferta,
    TipoTariffa tipoTariffa,
    Prezzi prezzi,
    Prezzi spread,
    BigDecimal pcvAnnuo,
    boolean attiva,
    String note,
    List<VoceDTO> voci) {
  public record VoceDTO(
      Long id, String tipo, Fascia fascia, BigDecimal corrispettivo, boolean indicizzata) {}

  public static OffertaResponse da(Offerta o) {
    return new OffertaResponse(
        o.getId(),
        o.getVersione(),
        o.getNomeFornitore(),
        o.getNomeOfferta(),
        o.getTipoOfferta(),
        o.getTipoTariffa(),
        o.getPrezzi(),
        o.getSpread(),
        o.getPcvAnnuo(),
        o.isAttiva(),
        o.getNote(),
        o.getVoci().stream()
            .map(
                v ->
                    new VoceDTO(
                        v.getId(),
                        v.getTipo().name(),
                        v.getFascia(),
                        v.getCorrispettivo(),
                        v.isIndicizzata()))
            .toList());
  }
}
