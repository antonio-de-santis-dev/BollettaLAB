CREATE TABLE offerte (
  workspace_id VARCHAR(36) NOT NULL DEFAULT 'test', author_id VARCHAR(36), author_name VARCHAR(200), company_name VARCHAR(200), company_vat VARCHAR(20), company_address VARCHAR(300),
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    versione BIGINT NOT NULL,
    nome_fornitore VARCHAR(150) NOT NULL,
    nome_offerta VARCHAR(150) NOT NULL,
    tipo_offerta VARCHAR(30) NOT NULL CHECK (tipo_offerta IN ('PREZZO_FISSO','INDICIZZATA_PUN')),
    tipo_tariffa VARCHAR(30) NOT NULL CHECK (tipo_tariffa IN ('MONORARIA','BIORARIA','TRIORARIA')),
    prezzo_f0 NUMERIC(16,8), prezzo_f1 NUMERIC(16,8), prezzo_f23 NUMERIC(16,8), prezzo_f2 NUMERIC(16,8), prezzo_f3 NUMERIC(16,8),
    spread_f0 NUMERIC(16,8), spread_f1 NUMERIC(16,8), spread_f23 NUMERIC(16,8), spread_f2 NUMERIC(16,8), spread_f3 NUMERIC(16,8),
    pcv_annuo NUMERIC(16,8) NOT NULL CHECK (pcv_annuo >= 0),
    attiva BOOLEAN NOT NULL,
    note VARCHAR(2000)
);
CREATE TABLE voci_corrispettivo (
  workspace_id VARCHAR(36) NOT NULL DEFAULT 'test', author_id VARCHAR(36), author_name VARCHAR(200), company_name VARCHAR(200), company_vat VARCHAR(20), company_address VARCHAR(300),
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    offerta_id BIGINT NOT NULL,
    tipo VARCHAR(30) NOT NULL CHECK (tipo IN ('ENERGIA','PCV')),
    fascia VARCHAR(10),
    corrispettivo NUMERIC(16,8) NOT NULL CHECK (corrispettivo >= 0),
    indicizzata BOOLEAN NOT NULL,
    CHECK ((tipo='PCV' AND fascia IS NULL AND indicizzata=FALSE) OR (tipo='ENERGIA' AND fascia IN ('F0','F1','F23','F2','F3'))),
  FOREIGN KEY (offerta_id) REFERENCES offerte(id)
);
CREATE INDEX idx_voci_offerta ON voci_corrispettivo(offerta_id);
CREATE TABLE bollette (
  workspace_id VARCHAR(36) NOT NULL DEFAULT 'test', author_id VARCHAR(36), author_name VARCHAR(200), company_name VARCHAR(200), company_vat VARCHAR(20), company_address VARCHAR(300),
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    versione BIGINT NOT NULL,
    cliente VARCHAR(150) NOT NULL, pod VARCHAR(30) NOT NULL, fornitore VARCHAR(150) NOT NULL,
    potenza_kw NUMERIC(8,4) NOT NULL CHECK (potenza_kw > 0),
    totale_fatturato NUMERIC(12,2) NOT NULL CHECK (totale_fatturato >= 0),
    aliquota_iva NUMERIC(5,4) NOT NULL CHECK (aliquota_iva BETWEEN 0 AND 1),
    altre_partite_imponibili NUMERIC(12,2) NOT NULL,
    altre_partite_esenti NUMERIC(12,2) NOT NULL
);
CREATE TABLE mesi_bolletta (
  workspace_id VARCHAR(36) NOT NULL DEFAULT 'test', author_id VARCHAR(36), author_name VARCHAR(200), company_name VARCHAR(200), company_vat VARCHAR(20), company_address VARCHAR(300),
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bolletta_id BIGINT NOT NULL, mese VARCHAR(7) NOT NULL,
    f1 NUMERIC(14,6) NOT NULL CHECK (f1 >= 0), f2 NUMERIC(14,6) NOT NULL CHECK (f2 >= 0), f3 NUMERIC(14,6) NOT NULL CHECK (f3 >= 0),
    pun_f0 NUMERIC(16,8), pun_f1 NUMERIC(16,8), pun_f23 NUMERIC(16,8), pun_f2 NUMERIC(16,8), pun_f3 NUMERIC(16,8),
    UNIQUE (bolletta_id,mese),
  FOREIGN KEY (bolletta_id) REFERENCES bollette(id)
);
CREATE TABLE parametri_gestore (
  workspace_id VARCHAR(36) NOT NULL DEFAULT 'test', author_id VARCHAR(36), author_name VARCHAR(200), company_name VARCHAR(200), company_vat VARCHAR(20), company_address VARCHAR(300),
    id BIGINT AUTO_INCREMENT PRIMARY KEY , versione BIGINT NOT NULL, configurazione LONGTEXT NOT NULL
);
CREATE TABLE confronti (
  workspace_id VARCHAR(36) NOT NULL DEFAULT 'test', author_id VARCHAR(36), author_name VARCHAR(200), company_name VARCHAR(200), company_vat VARCHAR(20), company_address VARCHAR(300),
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    creato_il TIMESTAMP(6) NOT NULL, snapshot LONGTEXT NOT NULL
);

CREATE TABLE impostazioni_pdf (
  workspace_id VARCHAR(36) NOT NULL DEFAULT 'test', author_id VARCHAR(36), author_name VARCHAR(200), company_name VARCHAR(200), company_vat VARCHAR(20), company_address VARCHAR(300),
    id BIGINT AUTO_INCREMENT PRIMARY KEY ,
    versione BIGINT NOT NULL,
    configurazione LONGTEXT NOT NULL
);
INSERT INTO impostazioni_pdf (id, versione, configurazione) VALUES (1, 0,
'{"stile":"CLASSICO","colore":"#194D3D","logo":null,"consulente":{"nome":"Andrea Bianchi · Studio Energia","ruolo":"Consulente energetico","email":"consulente@example.com","telefono":"+39 000 000 0000","indirizzo":"Via Esempio 12 · Lecce","dimostrativo":true}}');

CREATE TABLE dati_ufficiali (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 versione BIGINT NOT NULL,
 codice VARCHAR(80) NOT NULL,
 periodo VARCHAR(7) NOT NULL,
 categoria VARCHAR(40) NOT NULL,
 valore_ufficiale NUMERIC(20,8),
 valore_manuale NUMERIC(20,8),
 fonte VARCHAR(100) NOT NULL,
 url VARCHAR(1000) NOT NULL,
 pubblicato_il DATE,
 acquisito_il TIMESTAMP(6) NOT NULL,
 UNIQUE(codice,periodo,categoria)
);
CREATE TABLE revisioni_fonti (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 dato_id BIGINT NOT NULL,
 creato_il TIMESTAMP(6) NOT NULL,
 motivo VARCHAR(500) NOT NULL,
 contenuto LONGTEXT NOT NULL,
  FOREIGN KEY (dato_id) REFERENCES dati_ufficiali(id)
);
CREATE TABLE sincronizzazioni_fonti (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 creato_il TIMESTAMP(6) NOT NULL,
 fonte VARCHAR(100) NOT NULL,
 successo BOOLEAN NOT NULL,
 messaggio VARCHAR(500) NOT NULL
);
CREATE INDEX idx_fonti_periodo_categoria ON dati_ufficiali(periodo,categoria);
CREATE INDEX idx_revisioni_dato ON revisioni_fonti(dato_id,id);
CREATE INDEX idx_offerte_workspace ON offerte(workspace_id);

CREATE INDEX idx_voci_corrispettivo_workspace ON voci_corrispettivo(workspace_id);

CREATE INDEX idx_bollette_workspace ON bollette(workspace_id);

CREATE INDEX idx_mesi_bolletta_workspace ON mesi_bolletta(workspace_id);

CREATE INDEX idx_parametri_gestore_workspace ON parametri_gestore(workspace_id);

CREATE INDEX idx_confronti_workspace ON confronti(workspace_id);

CREATE INDEX idx_impostazioni_pdf_workspace ON impostazioni_pdf(workspace_id);

CREATE TABLE local_receipts(id VARCHAR(36) PRIMARY KEY,workspace_id VARCHAR(36) NOT NULL,result_id BIGINT NOT NULL,state VARCHAR(20) NOT NULL,response_json LONGTEXT,created_at TIMESTAMP NOT NULL);
