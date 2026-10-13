CREATE TABLE activity_outbox(id VARCHAR(36) PRIMARY KEY,payload LONGTEXT NOT NULL,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE INDEX idx_activity_outbox_created ON activity_outbox(created_at,id);
