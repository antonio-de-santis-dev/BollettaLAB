package it.progettogas.gas;

import static org.assertj.core.api.Assertions.*;

import it.bollettalab.platform.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class TenantIsolationTest {
  @Autowired Records records;
  @Autowired GasService service;

  Identity actor(String workspace, String user, String role) {
    return new Identity(
        user,
        workspace,
        role,
        "Operatore",
        "test@example.it",
        "ACTIVE",
        "Impresa",
        "12345678901",
        "Via Roma");
  }

  @AfterEach
  void clear() {
    RequestContext.clear();
  }

  @Test
  void idsQueriesAndAgentHistoryStayInScope() {
    String a = UUID.randomUUID().toString(),
        b = UUID.randomUUID().toString(),
        user = UUID.randomUUID().toString();
    RequestContext.set(actor(a, user, "OWNER"));
    var record = records.saveAndFlush(new RecordEntity("confronti", "{}"));
    assertThat(record.getWorkspaceId()).isEqualTo(a);
    RequestContext.set(actor(b, UUID.randomUUID().toString(), "OWNER"));
    assertThat(records.findById(record.id)).isEmpty();
    assertThat(records.findByKindOrderByIdDesc("confronti")).isEmpty();
    RequestContext.set(actor(a, UUID.randomUUID().toString(), "AGENT"));
    assertThat(records.findById(record.id)).isEmpty();
    assertThat(service.list("confronti")).isEmpty();
    RequestContext.set(actor(a, user, "OWNER"));
    assertThat(records.findById(record.id)).isPresent();
    assertThat(service.list("confronti")).hasSize(1);
  }
}
