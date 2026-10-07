package it.bollettalab.platform;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Serializes writes with permanent workspace deletion, including requests already authenticated.
 */
@Component
@ConditionalOnProperty(name = "platform.simulator", havingValue = "true")
public class WorkspaceGuard {
  private final JdbcTemplate db;

  public WorkspaceGuard(JdbcTemplate db) {
    this.db = db;
  }

  private boolean lock(String workspace) {
    try {
      db.update("INSERT INTO workspace_state(workspace_id,deleted) VALUES(?,FALSE)", workspace);
    } catch (DuplicateKeyException existing) {
    }
    return Boolean.TRUE.equals(
        db.queryForObject(
            "SELECT deleted FROM workspace_state WHERE workspace_id=? FOR UPDATE",
            Boolean.class,
            workspace));
  }

  @PrePersist
  @PreUpdate
  @PreRemove
  public void authorize(Object entity) {
    if (lock(RequestContext.workspace()))
      throw new HttpProblem(403, "Account eliminato: scrittura rifiutata");
  }

  public void markDeleted(String workspace) {
    lock(workspace);
    db.update("UPDATE workspace_state SET deleted=TRUE WHERE workspace_id=?", workspace);
  }
}
