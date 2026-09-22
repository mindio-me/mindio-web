-- spring-boot/src/main/resources/db/migration/mysql/V6__create_content_chunks.sql
-- 本文件在 db/migration/h2/ 下有同版本号的兄弟文件（h2/V6__create_content_chunks.sql），
-- 两者必须保持同步：相同版本号、相同 schema 语义，只允许 vendor 特定的类型/语法差异。
--
-- 笔记/收藏的语义检索基础设施：把内容分块+向量化存进这张表，查询时暴力算余弦相似度
-- （不引入独立向量数据库，理由见 docs/superpowers/specs/2026-09-01-notes-rag-retrieval-design.md）。
--
-- 这是全新建表，不是给已有表加列：dev 环境的 ddl-auto:update 可能在实体类加上去的那一刻就已经
-- 悄悄建出这张表，所以用 CREATE TABLE IF NOT EXISTS 保持幂等。MySQL 原生支持这个语法用在建表上
-- （不像 ADD COLUMN IF NOT EXISTS 那样需要 INFORMATION_SCHEMA + PREPARE/EXECUTE 迂回），
-- 所以外键约束也可以直接内联在 CREATE TABLE 里，不需要额外的存在性判断。
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
  UNIQUE KEY uk_content_chunks_source (source_type, source_id, chunk_index),
  KEY idx_content_chunks_owner (owner_id),
  CONSTRAINT fk_content_chunks_owner FOREIGN KEY (owner_id) REFERENCES users (id)
) ENGINE=InnoDB;
