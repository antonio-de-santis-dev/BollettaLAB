package it.progettoluce.business;

import static it.progettoluce.business.BusinessModels.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/business")
public class BusinessController {
  private final BusinessService service;
  private final BusinessPdf pdf;

  public BusinessController(BusinessService s, BusinessPdf p) {
    service = s;
    pdf = p;
  }

  @GetMapping("/profili")
  public List<Profilo> profili() {
    return service.profili();
  }

  @PostMapping("/profili")
  public ResponseEntity<Profilo> creaProfilo(@Valid @RequestBody ProfiloInput r) {
    var p = service.salva(null, r);
    return ResponseEntity.created(URI.create("/api/business/profili/" + p.id())).body(p);
  }

  @PutMapping("/profili/{id}")
  public Profilo salvaProfilo(@PathVariable @Positive Long id, @Valid @RequestBody ProfiloInput r) {
    return service.salva(id, r);
  }

  @GetMapping("/profili/{id}/revisioni")
  public List<Profilo> revisioni(@PathVariable @Positive Long id) {
    return service.revisioni(id);
  }

  @PostMapping("/simulazioni")
  public ResponseEntity<Simulazione> simula(@Valid @RequestBody SimulazioneInput r) {
    var s = service.crea(r);
    return ResponseEntity.created(URI.create("/api/business/simulazioni/" + s.id()))
        .cacheControl(CacheControl.noStore())
        .body(s);
  }

  @GetMapping("/simulazioni")
  public Pagina<Riepilogo> storico(
      @RequestParam(defaultValue = "0") @Min(0) int pagina,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int dimensione) {
    return service.storico(pagina, dimensione);
  }

  @GetMapping("/simulazioni/{id}")
  public ResponseEntity<Simulazione> leggi(@PathVariable @Positive Long id) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.leggi(id));
  }

  @GetMapping(value = "/simulazioni/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
  public ResponseEntity<byte[]> pdf(@PathVariable @Positive Long id) throws IOException {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=simulazione-business-" + id + ".pdf")
        .body(pdf.genera(service.leggi(id)));
  }
}
