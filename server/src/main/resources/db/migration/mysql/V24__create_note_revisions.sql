-- spring-boot/src/main/resources/db/migration/mysql/V24__create_note_revisions.sql
-- 本文件在 db/migration/h2/ 下有同版本号的兄弟文件，两者必须保持同步。
--
-- AI 助手直接编辑笔记：每一轮对话里某篇笔记第一次被改动之前存一份快照，供"撤销本轮修改"。
-- content_after 是本轮最后一次改动后的正文，撤销时和当前正文比较，判断用户之后是否又改过。
-- 仓库不用数据库外键维护笔记关联，删除笔记时由 NoteService 在代码里删除这里的记录。
-- ai_chat_messages.turn_id：助手回复所属的一轮（= 该轮用户消息 id），历史消息据此显示改动卡片。
-- 详见 docs/superpowers/specs/2026-10-11-agent-note-editing-design.md。
CREATE TABLE IF NOT EXISTS note_revisions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  note_id BIGINT NOT NULL,
  owner_id BIGINT NOT NULL,
  turn_id VARCHAR(64) NOT NULL,
  source VARCHAR(20) NOT NULL,
  content_type VARCHAR(20),
  content LONGTEXT,
  content_after LONGTEXT,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_note_revisions_turn_note UNIQUE (turn_id, note_id),
  KEY idx_note_revisions_note (note_id)
) ENGINE=InnoDB;

ALTER TABLE ai_chat_messages ADD COLUMN turn_id VARCHAR(64);
