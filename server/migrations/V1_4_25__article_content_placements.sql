-- Content metadata and reviewed links only. No article/role/finance data is published or enabled.
ALTER TABLE essay ADD COLUMN content_origin VARCHAR(20) NOT NULL DEFAULT 'manual',
                  ADD COLUMN source_url VARCHAR(2048) NULL;
CREATE TABLE ql_article_placement (
 placement_key VARCHAR(64) PRIMARY KEY,
 person_key VARCHAR(64) NOT NULL,
 essay_id BIGINT NULL,
 enabled BOOLEAN NOT NULL DEFAULT FALSE,
 authorization_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
 editorial_approved BOOLEAN NOT NULL DEFAULT FALSE,
 identity_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
 evidence_ref VARCHAR(500) NULL,
 article_hash VARCHAR(64) NULL,
 revision INT NOT NULL DEFAULT 0,
 updated_by BIGINT NULL,
 updated_at DATETIME(3) NULL
);
CREATE TABLE ql_article_placement_audit (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 placement_key VARCHAR(64) NOT NULL,
 revision INT NOT NULL,
 snapshot_json TEXT NOT NULL,
 operator_id BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uq_article_placement_revision(placement_key,revision)
);
INSERT INTO ql_article_placement(placement_key,person_key) VALUES
 ('about.huahua-family','huahua-family'),('about.leilei','leilei'),('about.founders','founders');
