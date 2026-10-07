package it.progettoluce.offerte;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/offerte")
public class OffertaController {
  private final OffertaService service;

  public OffertaController(OffertaService service) {
    this.service = service;
  }

  @GetMapping
  public List<OffertaResponse> lista() {
    return service.lista();
  }

  @GetMapping("/{id}")
  public OffertaResponse leggi(@PathVariable Long id) {
    return service.leggi(id);
  }

  @PostMapping
  public ResponseEntity<OffertaResponse> crea(@Valid @RequestBody OffertaRequest r) {
    OffertaResponse o = service.crea(r);
    return ResponseEntity.created(URI.create("/api/offerte/" + o.id())).body(o);
  }

  @PutMapping("/{id}")
  public OffertaResponse aggiorna(@PathVariable Long id, @Valid @RequestBody OffertaRequest r) {
    return service.aggiorna(id, r);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> elimina(@PathVariable Long id) {
    service.elimina(id);
    return ResponseEntity.noContent().build();
  }
}
