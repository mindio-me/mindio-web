-- spring-boot/src/main/resources/db/migration/h2/V6__create_content_chunks.sql
-- 本文件在 db/migration/mysql/ 下有同版本号的兄弟文件（mysql/V6__create_content_chunks.sql），
-- 两者必须保持同步：相同版本号、相同 schema 语义，只允许 vendor 特定的类型/语法差异。
--
-- 同 mysql/V6 文件头注释。H2 同样原生支持 CREATE TABLE IF NOT EXISTS，外键约束用
-- ADD CONSTRAINT IF NOT EXISTS 单独一条语句（H2 2.x 语法），保持和 mysql 版一致的幂等性。
CREATE TABLE IF NOT EXISTS content_chunks (
  id BIGINT NOT NULL AUTO_INCREMENT,
  owner_id BIGINT NOT NULL,
  source_type VARCHAR(20) NOT NULL,
  source_id BIGINT NOT NULL,
  chunk_index INT NOT NULL,
  chunk_text TEXT NOT NULL,
  content_hash VARCHAR(64) NOT NULL,
  embedding_json TEXT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE (source_type, source_id, chunk_index)
);
ALTER TABLE content_chunks ADD CONSTRAINT IF NOT EXISTS fk_content_chunks_owner
  FOREIGN KEY (owner_id) REFERENCES users (id);
