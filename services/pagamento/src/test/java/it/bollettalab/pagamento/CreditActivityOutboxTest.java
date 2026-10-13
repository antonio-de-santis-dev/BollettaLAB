package it.bollettalab.pagamento;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.bollettalab.platform.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/** Uses the real persistent outbox (the test profile otherwise replaces it with a no-op). */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:credit-outbox;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "platform.internal-key=01234567890123456789012345678901",
      "platform.audit.delivery-delay-ms=3600000"
    })
@ActiveProfiles("test")
class CreditActivityOutboxTest {
  @TestConfiguration
  static class RealOutbox {
    @Bean
    @org.springframework.context.annotation.Primary
    ActivitySink outbox(JdbcTemplate db, ObjectMapper json) {
      return new ActivityOutbox(db, json, "http://127.0.0.1:1", "internal-test");
    }
  }

  @Autowired Wallets wallets;
  @Autowired JdbcTemplate db;
  @Autowired TransactionTemplate transactions;

  int queued(String workspace) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM activity_outbox WHERE payload LIKE ?",
        Integer.class,
        "%" + workspace + "%");
  }

  @Test
  void ledgerEventIsQueuedOnlyAfterCommit() {
    String committed = UUID.randomUUID().toString(), rolledBack = UUID.randomUUID().toString();
    wallets.ensure(committed);
    wallets.ensure(rolledBack);
    transactions.executeWithoutResult(
        s -> wallets.ledger(committed, "actor", -1, "Simulazione gas"));
    transactions.executeWithoutResult(
        s -> {
          wallets.ledger(rolledBack, "actor", -1, "Simulazione gas");
          s.setRollbackOnly();
        });
    assertThat(queued(committed)).isEqualTo(1);
    assertThat(queued(rolledBack)).isZero();
    assertThat(
            db.queryForObject(
                "SELECT COUNT(*) FROM credit_ledger WHERE workspace_id=?",
                Integer.class,
                rolledBack))
        .isZero();
  }
}
