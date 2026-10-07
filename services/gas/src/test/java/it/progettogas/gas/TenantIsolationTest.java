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
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
  @Autowired SimulatorInternalApi internalApi;
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

  @Test
  void deletingAWorkspaceKeepsOtherReceiptsEvenWhenResultIdsOverlap() {
    String a = UUID.randomUUID().toString(),
        b = UUID.randomUUID().toString(),
        ra = UUID.randomUUID().toString(),
        rb = UUID.randomUUID().toString();
    for (var pair : java.util.List.of(java.util.List.of(ra, a), java.util.List.of(rb, b)))
      jdbc.update(
          "INSERT INTO local_receipts(id,workspace_id,result_id,state,response_json,created_at)"
              + " VALUES(?,?,1,'COMPLETE','{}',CURRENT_TIMESTAMP)",
          pair.get(0),
          pair.get(1));
    internalApi.purge(a);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM local_receipts WHERE workspace_id=?", Integer.class, a))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM local_receipts WHERE workspace_id=?", Integer.class, b))
        .isEqualTo(1);
  }

  @Test
  void aRequestAuthenticatedBeforeDeletionCannotRecreateData() {
    String workspace = UUID.randomUUID().toString();
    RequestContext.set(actor(workspace, UUID.randomUUID().toString(), "OWNER"));
    records.saveAndFlush(new RecordEntity("confronti", "{}"));
    internalApi.purge(workspace);
    assertThatThrownBy(() -> records.saveAndFlush(new RecordEntity("confronti", "{}")))
        .isInstanceOf(RuntimeException.class);
    assertThat(records.findByKindOrderByIdDesc("confronti")).isEmpty();
  }
}
