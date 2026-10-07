package it.progettoluce.business;

import it.progettoluce.offerte.Prezzi;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Map;

public final class BusinessModels {
  private BusinessModels() {}

  public enum Categoria {
    ENERGIA,
    TRASPORTO,
    ONERI,
    IMPOSTE,
    ALTRE_PARTITE
  }

  public enum Base {
    KWH_FASCIA,
    PERDITE_FASCIA,
    KWH_NETTI,
    KWH_CON_PERDITE,
    KW_MESE,
    KW_ANNO,
    QUOTA_MESE,
    QUOTA_ANNO
  }

  public enum Fascia {
    F0,
    F1,
    F2,
    F3,
    F23
  }

  public record Voce(
      @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,40}") String codice,
      @NotBlank @Size(max = 150) String descrizione,
      @NotNull Categoria categoria,
      @NotNull Base base,
      Fascia fascia,
      @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 8) BigDecimal corrispettivo,
      boolean indicizzata,
      @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer = 1, fraction = 4)
          BigDecimal aliquotaIva) {}

  public record ProfiloInput(
      @NotBlank @Size(max = 150) String nome,
      @NotBlank @Size(max = 500) String fonte,
      @NotNull @Pattern(regexp = "[0-9]{4}-(0[1-9]|1[0-2])") String dal,
      @Pattern(regexp = "[0-9]{4}-(0[1-9]|1[0-2])") String al,
      boolean verificato,
      @Size(max = 1000) String notaVerifica,
      @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer = 1, fraction = 8)
          BigDecimal perdite,
      boolean arrotondaPerdite,
      @NotEmpty @Size(max = 60) List<@NotNull @Valid Voce> voci,
      @PositiveOrZero Long versione) {}

  public record Profilo(Long id, Long versione, ProfiloInput dati) {}

  public record MeseInput(
      @NotNull @Pattern(regexp = "[0-9]{4}-(0[1-9]|1[0-2])") String mese,
      @NotNull @Positive Long profiloId,
      @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 6) BigDecimal f1,
      @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 6) BigDecimal f2,
      @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 6) BigDecimal f3,
      @Min(0) @Max(1) int quoteFisse,
      @Valid Prezzi pun) {}

  public record Partita(
      @NotBlank @Size(max = 150) String descrizione,
      @NotNull @Digits(integer = 10, fraction = 2) BigDecimal importo,
      boolean esente,
      @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer = 1, fraction = 4)
          BigDecimal aliquotaIva) {}

  public record SimulazioneInput(
      @NotBlank @Size(max = 150) String ragioneSociale,
      @NotBlank @Size(max = 30) String pod,
      @Pattern(regexp = "[0-9]{11}|", message = "Usa 11 cifre oppure lascia vuoto")
          String partitaIva,
      @NotNull LocalDate dataRiferimento,
      @NotNull @Positive @Digits(integer = 4, fraction = 4) BigDecimal potenzaKw,
      @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal fatturaPrecedente,
      boolean confermaConfrontabilita,
      @NotEmpty @Size(max = 12) List<@NotNull @Valid MeseInput> mesi,
      @NotNull @Size(max = 30) List<@NotNull @Valid Partita> altrePartite) {}

  public record Riga(
      String mese,
      String codice,
      String descrizione,
      Categoria categoria,
      String unita,
      BigDecimal quantita,
      BigDecimal corrispettivo,
      BigDecimal imponibile,
      BigDecimal aliquotaIva,
      boolean esente) {}

  public record Iva(BigDecimal aliquota, BigDecimal imponibile, BigDecimal imposta) {}

  public record Mensile(
      String mese, Profilo profilo, Map<Categoria, BigDecimal> categorie, BigDecimal imponibile) {}

  public record Risultato(
      List<Riga> righe,
      List<Mensile> mesi,
      Map<Categoria, BigDecimal> categorie,
      Map<Categoria, BigDecimal> incidenze,
      List<Iva> iva,
      BigDecimal imponibile,
      BigDecimal totaleIva,
      BigDecimal esenti,
      BigDecimal totale,
      BigDecimal risparmioPeriodo,
      BigDecimal risparmioPercentuale,
      BigDecimal risparmioAnnualizzato,
      int numeroMesi,
      List<String> avvisi) {}

  public record Snapshot(String versioneMotore, SimulazioneInput input, Risultato risultato) {}

  public record Simulazione(Long id, Instant creataIl, Snapshot dati) {}

  public record Riepilogo(
      Long id,
      Instant creataIl,
      String ragioneSociale,
      String dal,
      String al,
      BigDecimal totale,
      BigDecimal risparmio,
      boolean bozza) {}

  public record Pagina<T>(List<T> contenuto, long totaleElementi, int pagina, int dimensione) {}
}
