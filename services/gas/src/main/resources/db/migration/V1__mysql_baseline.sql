CREATE TABLE gas_records (
  workspace_id VARCHAR(36) NOT NULL DEFAULT 'test', author_id VARCHAR(36), author_name VARCHAR(200), company_name VARCHAR(200), company_vat VARCHAR(20), company_address VARCHAR(300),
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  version BIGINT NOT NULL DEFAULT 0,
  kind VARCHAR(30) NOT NULL,
  payload LONGTEXT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL
);
CREATE INDEX idx_gas_records_kind_id ON gas_records(kind, id);
CREATE INDEX idx_gas_records_workspace ON gas_records(workspace_id);

CREATE TABLE local_receipts(id VARCHAR(36) PRIMARY KEY,workspace_id VARCHAR(36) NOT NULL,result_id BIGINT NOT NULL,state VARCHAR(20) NOT NULL,response_json LONGTEXT,created_at TIMESTAMP NOT NULL);
