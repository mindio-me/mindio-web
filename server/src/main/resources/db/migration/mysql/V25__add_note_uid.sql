-- spring-boot/src/main/resources/db/migration/mysql/V25__add_note_uid.sql
-- 本文件在 db/migration/h2/ 下有同版本号的兄弟文件，两者必须保持同步。
--
-- 笔记的跨安装身份，供 .mindio 包导出/导入识别"同一篇笔记"。按需分配，不回填已有笔记。
-- 多用户服务器上同一个包可能被不同用户导入，所以唯一性按 (owner_id, uid)。
-- 详见 docs/superpowers/specs/2026-10-12-editorjs-note-export-import-design.md。
ALTER TABLE notes ADD COLUMN uid VARCHAR(36) NULL;
ALTER TABLE notes ADD CONSTRAINT uk_notes_owner_uid UNIQUE (owner_id, uid);
