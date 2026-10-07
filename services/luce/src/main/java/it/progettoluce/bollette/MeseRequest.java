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
    @Valid Prezzi pun) {}
