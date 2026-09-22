-- spring-boot/src/main/resources/db/migration/h2/V8__add_attachments_to_ai_chat_messages.sql
-- 本文件在 db/migration/mysql/ 下有同版本号的兄弟文件，两者必须保持同步。
ALTER TABLE ai_chat_messages ADD COLUMN IF NOT EXISTS attachments_json TEXT;
