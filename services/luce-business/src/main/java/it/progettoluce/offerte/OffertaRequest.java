package it.progettoluce.offerte;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record OffertaRequest(
    @NotBlank @Size(max = 150) String nomeFornitore,
    @NotBlank @Size(max = 150) String nomeOfferta,
    @NotNull TipoOfferta tipoOfferta,
    @NotNull TipoTariffa tipoTariffa,
    @Valid Prezzi prezzi,
    @Valid Prezzi spread,
    @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 8) BigDecimal pcvAnnuo,
    boolean attiva,
    @Size(max = 2000) String note,
    @PositiveOrZero Long versione) {}
