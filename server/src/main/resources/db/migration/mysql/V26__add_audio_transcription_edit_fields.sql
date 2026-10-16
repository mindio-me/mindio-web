-- spring-boot/src/main/resources/db/migration/mysql/V26__add_audio_transcription_edit_fields.sql
-- 本文件在 db/migration/h2/ 下有同版本号的兄弟文件，两者必须保持同步。
--
-- 录音第二阶段：场景模板（scene / scene_source）、说话人改名（speaker_names，JSON）、
-- 原文被用户修改的时间（edited_at）。逐句的 speaker 编号存在 utterances JSON 里，不加列。
-- 详见 docs/superpowers/specs/2026-10-16-recording-phase2-design.md。
--
-- 幂等：dev 环境 ddl-auto:update 可能已建过这几列；真实 MySQL 不支持 ADD COLUMN IF NOT EXISTS，
-- 照 V25 用 INFORMATION_SCHEMA + PREPARE/EXECUTE。四列总是一起出现，以 scene 列为准判断。
SET @col_stmt := (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'audio_transcriptions' AND COLUMN_NAME = 'scene') = 0,
    'ALTER TABLE audio_transcriptions ADD COLUMN scene VARCHAR(20) NULL, ADD COLUMN scene_source VARCHAR(10) NULL, ADD COLUMN speaker_names LONGTEXT NULL, ADD COLUMN edited_at DATETIME(6) NULL',
    'SELECT 1'
  )
);
PREPARE col_stmt FROM @col_stmt;
EXECUTE col_stmt;
DEALLOCATE PREPARE col_stmt;
