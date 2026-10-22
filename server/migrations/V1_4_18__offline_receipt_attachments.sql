-- Optional external references remain external facts. Separate request keys protect retries.
ALTER TABLE ql_transaction
  ADD COLUMN receipt_request_id char(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  ADD UNIQUE KEY uk_transaction_receipt_request (receipt_request_id);
-- Private images follow the existing pass/interview BLOB storage pattern; no public URL.
CREATE TABLE ql_offline_receipt_image (
  id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  transaction_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  position tinyint NOT NULL,
  image_mime varchar(32) NOT NULL,
  image_sha256 char(64) NOT NULL,
  image_data mediumblob NOT NULL,
  UNIQUE KEY uk_receipt_image_position (transaction_id,position),
  CONSTRAINT fk_receipt_image_transaction FOREIGN KEY (transaction_id) REFERENCES ql_transaction(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
