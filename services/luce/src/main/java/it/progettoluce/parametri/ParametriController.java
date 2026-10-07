package it.progettoluce.parametri;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/parametri")
public class ParametriController {
  private final ParametriService service;

  public ParametriController(ParametriService service) {
    this.service = service;
  }

  @GetMapping
  public ParametriRequest leggi() {
    return service.leggi();
  }

  @PutMapping
  public ParametriRequest salva(@Valid @RequestBody ParametriRequest r) {
    return service.salva(r);
  }
}
