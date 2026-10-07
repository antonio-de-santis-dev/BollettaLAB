package it.bollettalab.platform;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class PlatformErrors {
  @ExceptionHandler(HttpProblem.class)
  public ResponseEntity<?> problem(HttpProblem e) {
    return ResponseEntity.status(e.status()).body(Map.of("message", e.getMessage()));
  }
}
