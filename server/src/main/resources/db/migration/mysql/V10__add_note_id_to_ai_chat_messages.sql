-- spring-boot/src/main/resources/db/migration/mysql/V10__add_note_id_to_ai_chat_messages.sql
-- 本文件在 db/migration/h2/ 下有同版本号的兄弟文件，两者必须保持同步。
ALTER TABLE ai_chat_messages ADD COLUMN IF NOT EXISTS note_id BIGINT;
