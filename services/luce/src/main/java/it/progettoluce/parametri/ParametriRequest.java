package it.progettoluce.parametri;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record ParametriRequest(
    @NotBlank @Size(max = 150) String nomeProfilo,
    @NotBlank @Size(max = 500) String fonte,
    @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer = 1, fraction = 8)
        BigDecimal coefficientePerdite,
    boolean arrotondaPerdite,
    @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 8) BigDecimal dispacciamentoKwh,
    @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 8) BigDecimal trasportoFissoMese,
    @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 8) BigDecimal trasportoPotenzaAnno,
    @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 8) BigDecimal trasportoKwh,
    @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 8) BigDecimal oneriFissiMese,
    @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 8) BigDecimal oneriKwh,
    @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 8) BigDecimal accisaKwh,
    @PositiveOrZero Long versione) {}
