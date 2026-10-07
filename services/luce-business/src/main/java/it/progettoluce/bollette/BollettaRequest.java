package it.progettoluce.bollette;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record BollettaRequest(
    @NotBlank @Size(max = 150) String cliente,
    @NotBlank @Size(max = 30) String pod,
    @NotBlank @Size(max = 150) String fornitore,
    @NotNull @Positive @Digits(integer = 4, fraction = 4) BigDecimal potenzaKw,
    @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal totaleFatturato,
    @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer = 1, fraction = 4)
        BigDecimal aliquotaIva,
    @NotNull @Digits(integer = 10, fraction = 2) BigDecimal altrePartiteImponibili,
    @NotNull @Digits(integer = 10, fraction = 2) BigDecimal altrePartiteEsenti,
    @NotEmpty @Size(max = 12) List<@NotNull @Valid MeseRequest> mesi,
    @PositiveOrZero Long versione,
    @Pattern(regexp = "[0-9]{11}|", message = "Partita IVA: 11 cifre oppure vuoto")
        String partitaIva) {
  public BollettaRequest(
      String cliente,
      String pod,
      String fornitore,
      BigDecimal potenzaKw,
      BigDecimal totaleFatturato,
      BigDecimal aliquotaIva,
      BigDecimal altrePartiteImponibili,
      BigDecimal altrePartiteEsenti,
      List<MeseRequest> mesi,
      Long versione) {
    this(
        cliente,
        pod,
        fornitore,
        potenzaKw,
        totaleFatturato,
        aliquotaIva,
        altrePartiteImponibili,
        altrePartiteEsenti,
        mesi,
        versione,
        null);
  }
}
