-- spring-boot/src/main/resources/db/migration/mysql/V12__create_local_file_extractions.sql
-- 本文件在 db/migration/h2/ 下有同版本号的兄弟文件，两者必须保持同步。
--
-- 真实 MySQL（8.0，本项目自建部署目标）不支持 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`（V5/V8 已踩过这个坑），
-- 用 INFORMATION_SCHEMA 判断 + PREPARE/EXECUTE 动态 SQL 实现等价的幂等操作。
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
  UNIQUE KEY uk_local_file_extractions_content_hash (content_hash)
) ENGINE=InnoDB;

SET @col_stmt := (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'local_media_files' AND COLUMN_NAME = 'content_hash') = 0,
    'ALTER TABLE local_media_files ADD COLUMN content_hash VARCHAR(64)',
    'SELECT 1'
  )
);
PREPARE col_stmt FROM @col_stmt;
EXECUTE col_stmt;
DEALLOCATE PREPARE col_stmt;

SET @col_stmt2 := (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'attachments' AND COLUMN_NAME = 'content_hash') = 0,
    'ALTER TABLE attachments ADD COLUMN content_hash VARCHAR(64)',
    'SELECT 1'
  )
);
PREPARE col_stmt2 FROM @col_stmt2;
EXECUTE col_stmt2;
DEALLOCATE PREPARE col_stmt2;
