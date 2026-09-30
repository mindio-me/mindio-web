-- spring-boot/src/main/resources/db/migration/h2/V21__cutover_ai_chat_messages_to_conversations.sql
-- 本文件在 db/migration/mysql/ 下有同版本号的兄弟文件，两者必须保持同步。
ALTER TABLE ai_chat_messages ALTER COLUMN conversation_id BIGINT NOT NULL;
ALTER TABLE ai_chat_messages ADD CONSTRAINT IF NOT EXISTS fk_ai_chat_messages_conversation
  FOREIGN KEY (conversation_id) REFERENCES ai_chat_conversations (id) ON DELETE CASCADE;
ALTER TABLE ai_chat_messages DROP CONSTRAINT IF EXISTS fk_ai_chat_messages_owner;
ALTER TABLE ai_chat_messages DROP COLUMN IF EXISTS owner_id;
ALTER TABLE ai_chat_messages DROP COLUMN IF EXISTS note_id;
