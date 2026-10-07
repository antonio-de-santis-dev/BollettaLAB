package it.progettoluce.fonti;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fonti")
@Validated
public class FontiController {
  public record Manuale(
      @NotBlank String codice,
      @NotBlank @Pattern(regexp = "[0-9]{4}-(0[1-9]|1[0-2])") String periodo,
      @NotBlank String categoria,
      @NotNull BigDecimal valore,
      @PositiveOrZero Long versione,
      @NotBlank @Size(max = 500) String motivo) {}

  public record Ripristino(
      @NotNull @PositiveOrZero Long versione, @NotBlank @Size(max = 500) String motivo) {}

  private final ArchivioFonti archivio;
  private final SincronizzazioneFonti sync;

  public FontiController(ArchivioFonti archivio, SincronizzazioneFonti sync) {
    this.archivio = archivio;
    this.sync = sync;
  }

  @GetMapping
  public SincronizzazioneFonti.Stato stato() {
    return sync.stato();
  }

  @GetMapping("/dati")
  public List<ArchivioFonti.Dato> dati(
      @RequestParam @Pattern(regexp = "[0-9]{4}-(0[1-9]|1[0-2])") String periodo,
      @RequestParam
          @Pattern(regexp = "DOMESTICO_RESIDENTE|DOMESTICO_NON_RESIDENTE|INDICE|ORIGINALE")
          String categoria) {
    return archivio.lista(periodo, categoria);
  }

  @PostMapping("/sincronizza")
  public SincronizzazioneFonti.Stato sincronizza(
      @RequestParam(required = false) @Pattern(regexp = "[0-9]{4}-(0[1-9]|1[0-2])")
          String periodoGme) {
    return sync.sincronizza(periodoGme);
  }

  @PutMapping("/dati")
  public ArchivioFonti.Dato manuale(@Valid @RequestBody Manuale r) {
    return archivio.manuale(
        r.codice(), r.periodo(), r.categoria(), r.valore(), r.versione(), r.motivo().trim());
  }

  @PostMapping("/dati/{id}/ripristina")
  public ArchivioFonti.Dato ripristina(
      @PathVariable @Positive Long id, @Valid @RequestBody Ripristino r) {
    return archivio.ripristina(id, r.versione(), r.motivo().trim());
  }

  @GetMapping("/dati/{id}/revisioni")
  public List<ArchivioFonti.Revisione> revisioni(@PathVariable @Positive Long id) {
    return archivio.revisioni(id);
  }
}
