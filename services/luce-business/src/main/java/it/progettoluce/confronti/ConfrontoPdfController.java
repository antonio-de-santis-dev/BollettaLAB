package it.progettoluce.confronti;

import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/confronti")
public class ConfrontoPdfController {
  private final ConfrontoService confronti;
  private final ConfrontoPdfRenderer renderer;
  private final ImpostazioniPdfService impostazioni;

  public ConfrontoPdfController(
      ConfrontoService confronti,
      ConfrontoPdfRenderer renderer,
      ImpostazioniPdfService impostazioni) {
    this.confronti = confronti;
    this.renderer = renderer;
    this.impostazioni = impostazioni;
  }

  @PostMapping(value = "/{id}/pdf/anteprima", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<PdfAnteprima> anteprima(
      @PathVariable Long id, @Valid @RequestBody PdfPersonalizzazione opzioni) throws IOException {
    byte[] bytes = renderer.genera(confronti.leggi(id), opzioni);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(PdfAnteprima.da(bytes));
  }

  @PostMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
  public ResponseEntity<byte[]> personalizza(
      @PathVariable Long id, @Valid @RequestBody PdfPersonalizzazione opzioni) throws IOException {
    return risposta(id, renderer.genera(confronti.leggi(id), opzioni));
  }

  @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
  public ResponseEntity<byte[]> scarica(@PathVariable Long id) throws IOException {
    byte[] pdf = renderer.genera(confronti.leggi(id), impostazioni.leggi().opzioni());
    return risposta(id, pdf);
  }

  private ResponseEntity<byte[]> risposta(Long id, byte[] pdf) {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename("confronto-" + id + ".pdf").build().toString())
        .cacheControl(CacheControl.noStore())
        .body(pdf);
  }
}
