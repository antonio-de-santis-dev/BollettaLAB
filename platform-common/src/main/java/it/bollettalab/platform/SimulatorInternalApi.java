package it.bollettalab.platform;

import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name = "platform.simulator", havingValue = "true")
public class SimulatorInternalApi {
  final JdbcTemplate db;
  final Environment env;

  public SimulatorInternalApi(JdbcTemplate db, Environment env) {
    this.db = db;
    this.env = env;
  }

  @GetMapping("/internal/operations/{id}")
  Object operation(@PathVariable String id) {
    var rows = db.queryForList("SELECT result_id,response_json FROM local_receipts WHERE id=?", id);
    if (!rows.isEmpty())
      return Map.of(
          "saved",
          true,
          "resultId",
          rows.get(0).get("result_id"),
          "response",
          rows.get(0).get("response_json"));
    return Map.of("saved", false, "running", CreditFilter.running.contains(id));
  }

  @DeleteMapping("/internal/workspaces/{id}")
  @Transactional
  Object purge(@PathVariable String id) {
    if ("gas".equals(env.getProperty("platform.service"))) {
      db.update(
          "DELETE FROM local_receipts WHERE result_id IN (SELECT id FROM gas_records WHERE"
              + " workspace_id=?)",
          id);
      db.update("DELETE FROM gas_records WHERE workspace_id=?", id);
    } else {
      db.update(
          "DELETE FROM local_receipts WHERE result_id IN (SELECT id FROM confronti WHERE"
              + " workspace_id=?)",
          id);
      if ("luce-business".equals(env.getProperty("platform.service"))) {
        db.update(
            "DELETE FROM local_receipts WHERE result_id IN (SELECT id FROM business_simulazioni"
                + " WHERE workspace_id=?)",
            id);
        db.update(
            "DELETE FROM business_revisioni_profili WHERE profilo_id IN (SELECT id FROM"
                + " business_profili WHERE workspace_id=?)",
            id);
        for (String t : List.of("business_simulazioni", "business_profili"))
          db.update("DELETE FROM " + t + " WHERE workspace_id=?", id);
      }
      for (String t :
          List.of(
              "confronti",
              "voci_corrispettivo",
              "mesi_bolletta",
              "offerte",
              "bollette",
              "parametri_gestore",
              "impostazioni_pdf")) db.update("DELETE FROM " + t + " WHERE workspace_id=?", id);
    }
    return Map.of("deleted", true);
  }
}
