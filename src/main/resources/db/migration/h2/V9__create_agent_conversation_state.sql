-- spring-boot/src/main/resources/db/migration/h2/V9__create_agent_conversation_state.sql
-- 本文件在 db/migration/mysql/ 下有同版本号的兄弟文件（mysql/V9__create_agent_conversation_state.sql），
-- 两者必须保持同步：相同版本号、相同 schema 语义，只允许 vendor 特定的类型/语法差异。
CREATE TABLE IF NOT EXISTS agent_conversation_state (
  id BIGINT NOT NULL AUTO_INCREMENT,
  conversation_id VARCHAR(255) NOT NULL,
  state_blob TEXT,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id)
);
ALTER TABLE agent_conversation_state ADD CONSTRAINT IF NOT EXISTS uk_agent_conversation_state_conversation_id
  UNIQUE (conversation_id);
