-- spring-boot/src/main/resources/db/migration/h2/V12__create_local_file_extractions.sql
-- 本文件在 db/migration/mysql/ 下有同版本号的兄弟文件，两者必须保持同步。
CREATE TABLE IF NOT EXISTS local_file_extractions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  content_hash VARCHAR(64) NOT NULL,
  extraction_type VARCHAR(20) NOT NULL,
  extracted_text TEXT,
  status VARCHAR(20) NOT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  processed_at DATETIME(6),
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE (content_hash)
);
ALTER TABLE local_media_files ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64);
ALTER TABLE attachments ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64);
