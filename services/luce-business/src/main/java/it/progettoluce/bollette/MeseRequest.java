package it.progettoluce.bollette;

import it.progettoluce.offerte.Prezzi;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record MeseRequest(
    @NotBlank @Pattern(regexp = "[0-9]{4}-(0[1-9]|1[0-2])") String mese,
    @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 6) BigDecimal f1,
    @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 6) BigDecimal f2,
    @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 6) BigDecimal f3,
    @Valid Prezzi pun,
    @Min(0) @Max(1) Integer quoteFisse) {
  public MeseRequest {
    if (quoteFisse == null) quoteFisse = 1;
  }

  public MeseRequest(String mese, BigDecimal f1, BigDecimal f2, BigDecimal f3, Prezzi pun) {
    this(mese, f1, f2, f3, pun, 1);
  }
}
