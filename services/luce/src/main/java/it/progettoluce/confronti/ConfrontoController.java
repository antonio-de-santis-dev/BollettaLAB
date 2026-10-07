package it.progettoluce.confronti;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/confronti")
public class ConfrontoController {
  private final ConfrontoService service;

  public ConfrontoController(ConfrontoService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<ConfrontoService.Risposta> crea(
      @Valid @RequestBody ConfrontoService.Richiesta r) {
    var c = service.crea(r);
    return ResponseEntity.created(URI.create("/api/confronti/" + c.id())).body(c);
  }

  @GetMapping
  public List<ConfrontoService.Risposta> lista() {
    return service.lista();
  }

  @GetMapping("/{id}")
  public ConfrontoService.Risposta leggi(@PathVariable Long id) {
    return service.leggi(id);
  }
}
