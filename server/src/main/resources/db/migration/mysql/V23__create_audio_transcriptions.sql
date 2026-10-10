-- spring-boot/src/main/resources/db/migration/mysql/V23__create_audio_transcriptions.sql
-- 本文件在 db/migration/h2/ 下有同版本号的兄弟文件，两者必须保持同步。
--
-- 录音自动成笔记：每个音频块（note_id + block_id）一条转写记录，存逐句原文、AI 优化版、
-- 结构化总结和处理状态。结果不写进笔记正文，避免和编辑器自动保存互相覆盖。
-- 仓库已不用数据库外键维护笔记关联，删除笔记时由 NoteService 在代码里删除这里的记录。
-- 详见 docs/superpowers/specs/2026-10-10-recording-auto-notes-design.md。
CREATE TABLE IF NOT EXISTS audio_transcriptions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  note_id BIGINT NOT NULL,
  block_id VARCHAR(64) NOT NULL,
  status VARCHAR(20) NOT NULL,
  trigger_type VARCHAR(10) NOT NULL,
  stage VARCHAR(10) NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  error_kind VARCHAR(30),
  error_message VARCHAR(1000),
  utterances LONGTEXT,
  polished LONGTEXT,
  summary LONGTEXT,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  completed_at DATETIME(6),
  PRIMARY KEY (id),
  CONSTRAINT uk_audio_transcriptions_note_block UNIQUE (note_id, block_id),
  KEY idx_audio_transcriptions_status (status)
) ENGINE=InnoDB;
