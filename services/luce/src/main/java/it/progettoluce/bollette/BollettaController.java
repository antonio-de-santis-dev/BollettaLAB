package it.progettoluce.bollette;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bollette")
public class BollettaController {
  private final BollettaService service;

  public BollettaController(BollettaService service) {
    this.service = service;
  }

  @GetMapping
  public List<BollettaService.Risposta> lista() {
    return service.lista();
  }

  @GetMapping("/{id}")
  public BollettaService.Risposta leggi(@PathVariable Long id) {
    return service.leggi(id);
  }

  @PostMapping
  public ResponseEntity<BollettaService.Risposta> crea(@Valid @RequestBody BollettaRequest r) {
    var b = service.crea(r);
    return ResponseEntity.created(URI.create("/api/bollette/" + b.id())).body(b);
  }

  @PutMapping("/{id}")
  public BollettaService.Risposta aggiorna(
      @PathVariable Long id, @Valid @RequestBody BollettaRequest r) {
    return service.aggiorna(id, r);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> elimina(@PathVariable Long id) {
    service.elimina(id);
    return ResponseEntity.noContent().build();
  }
}
