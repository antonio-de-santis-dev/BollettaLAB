package it.progettoluce.fonti;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DatoUfficialeRepository extends JpaRepository<DatoUfficiale, Long> {
  Optional<DatoUfficiale> findByCodiceAndPeriodoAndCategoria(
      String codice, String periodo, String categoria);

  List<DatoUfficiale> findByPeriodoAndCategoriaOrderByCodice(String periodo, String categoria);
}
