-- Explicit migration rehearsal only; not applied by application startup.
ALTER TABLE essay ADD COLUMN author_name VARCHAR(128) NULL COMMENT 'Preserved article byline, independent of backend user ID';

CREATE TABLE ql_article_source (
 source_system VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 source_id VARCHAR(191) COLLATE utf8mb4_bin NOT NULL,
 essay_id BIGINT NULL,
 source_url VARCHAR(2048) NOT NULL,
 source_hash CHAR(64) NULL,
 target_hash CHAR(64) NULL,
 source_status TINYINT NOT NULL,
 media_map JSON NULL,
 approval_ref VARCHAR(255) NOT NULL,
 batch_id VARCHAR(64) NOT NULL,
 imported_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(source_system,source_id), UNIQUE KEY uq_article_source_target(essay_id),
 CONSTRAINT fk_article_source_essay FOREIGN KEY(essay_id) REFERENCES essay(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE ql_article_label_source (
 source_system VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 source_id VARCHAR(191) COLLATE utf8mb4_bin NOT NULL,
 label_id BIGINT NULL,
 source_name VARCHAR(20) NULL,
 source_status TINYINT NULL,
 batch_id VARCHAR(64) NOT NULL,
 PRIMARY KEY(source_system,source_id),
 CONSTRAINT fk_article_label_source_label FOREIGN KEY(label_id) REFERENCES label(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE ql_article_import_attempt (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 batch_id VARCHAR(64) NOT NULL,
 source_system VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 source_id VARCHAR(191) COLLATE utf8mb4_bin NULL,
 outcome VARCHAR(24) NOT NULL,
 error_code VARCHAR(64) NULL,
 attempted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 KEY idx_article_attempt_batch(batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
