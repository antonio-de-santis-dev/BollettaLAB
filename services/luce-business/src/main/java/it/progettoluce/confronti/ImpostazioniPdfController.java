package it.progettoluce.confronti;

import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/impostazioni-pdf")
public class ImpostazioniPdfController {
  private final ImpostazioniPdfService impostazioni;
  private final ConfrontoPdfRenderer renderer;

  public ImpostazioniPdfController(
      ImpostazioniPdfService impostazioni, ConfrontoPdfRenderer renderer) {
    this.impostazioni = impostazioni;
    this.renderer = renderer;
  }

  @GetMapping
  public ResponseEntity<ImpostazioniPdfService.Configurazione> leggi() {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(impostazioni.leggi());
  }

  @PutMapping
  public ResponseEntity<ImpostazioniPdfService.Configurazione> salva(
      @Valid @RequestBody ImpostazioniPdfService.Configurazione request) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(impostazioni.salva(request));
  }

  @PostMapping(value = "/anteprima", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<PdfAnteprima> anteprima(@Valid @RequestBody PdfPersonalizzazione options)
      throws IOException {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(PdfAnteprima.da(renderer.genera(PdfEsempio.crea(), options)));
  }
}
