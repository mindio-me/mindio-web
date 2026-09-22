-- spring-boot/src/main/resources/db/migration/mysql/V7__create_ai_chat_messages.sql
-- 本文件在 db/migration/h2/ 下有同版本号的兄弟文件（h2/V7__create_ai_chat_messages.sql），
-- 两者必须保持同步：相同版本号、相同 schema 语义，只允许 vendor 特定的类型/语法差异。
--
-- 全局AI助手的单一连续对话历史，详见
-- docs/superpowers/specs/2026-09-02-global-chat-backend-design.md
--
-- 全新建表，用 CREATE TABLE IF NOT EXISTS 保持幂等（dev 环境 ddl-auto:update 可能已抢先建出这张表）。
CREATE TABLE IF NOT EXISTS ai_chat_messages (
  id BIGINT NOT NULL AUTO_INCREMENT,
  owner_id BIGINT NOT NULL,
  role VARCHAR(20) NOT NULL,
  content TEXT NOT NULL,
  citations_json TEXT,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_ai_chat_messages_owner (owner_id),
  CONSTRAINT fk_ai_chat_messages_owner FOREIGN KEY (owner_id) REFERENCES users (id)
) ENGINE=InnoDB;
