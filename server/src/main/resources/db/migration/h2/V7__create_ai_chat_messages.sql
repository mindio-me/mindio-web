-- spring-boot/src/main/resources/db/migration/h2/V7__create_ai_chat_messages.sql
-- 本文件在 db/migration/mysql/ 下有同版本号的兄弟文件（mysql/V7__create_ai_chat_messages.sql），
-- 两者必须保持同步：相同版本号、相同 schema 语义，只允许 vendor 特定的类型/语法差异。
CREATE TABLE IF NOT EXISTS ai_chat_messages (
  id BIGINT NOT NULL AUTO_INCREMENT,
  owner_id BIGINT NOT NULL,
  role VARCHAR(20) NOT NULL,
  content TEXT NOT NULL,
  citations_json TEXT,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id)
);
ALTER TABLE ai_chat_messages ADD CONSTRAINT IF NOT EXISTS fk_ai_chat_messages_owner
  FOREIGN KEY (owner_id) REFERENCES users (id);
