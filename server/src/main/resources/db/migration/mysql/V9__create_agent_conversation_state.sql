-- spring-boot/src/main/resources/db/migration/mysql/V9__create_agent_conversation_state.sql
-- 本文件在 db/migration/h2/ 下有同版本号的兄弟文件（h2/V9__create_agent_conversation_state.sql），
-- 两者必须保持同步：相同版本号、相同 schema 语义，只允许 vendor 特定的类型/语法差异。
--
-- 独立Python/LangGraph Agent服务的checkpointer持久化后端，详见
-- docs/superpowers/specs/2026-09-04-agent-service-langgraph-design.md
--
-- state_blob 对Java来说是不透明数据（LangGraph自己定义结构和演变逻辑），Java只做按
-- conversation_id 的存取，不解析内容，因此不关联 users 外键——conversation_id 目前等同于
-- username，归属信息已经承载在这个字符串里。
CREATE TABLE IF NOT EXISTS agent_conversation_state (
  id BIGINT NOT NULL AUTO_INCREMENT,
  conversation_id VARCHAR(255) NOT NULL,
  state_blob TEXT,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_agent_conversation_state_conversation_id (conversation_id)
) ENGINE=InnoDB;
