-- 本文件在 db/migration/h2/ 下有同版本号的兄弟文件
-- （h2/V14__add_audio_recording_clip_support.sql），两者必须保持同步：
-- 相同版本号、相同 schema 语义，只允许 vendor 特定的类型/语法差异。
--
-- source_clips.source_type 是真正的 MySQL ENUM 列（V1 建表时写死了四个值），新增
-- AUDIO_RECORDING（笔记录音功能，保存到素材库但不关联任何笔记的场景）需要用 MODIFY
-- COLUMN 重新声明允许的取值集合。duration_seconds 只有这一种 sourceType 会用到，
-- 其余 clip 类型留 NULL。
ALTER TABLE source_clips
    MODIFY COLUMN source_type ENUM('WEBPAGE','WECHAT_ARTICLE','WECHAT_CHAT_TEXT','WECHAT_CHAT_IMAGE','AUDIO_RECORDING') NOT NULL;

SET @stmt := (
  SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'source_clips' AND COLUMN_NAME = 'duration_seconds') = 0,
    'ALTER TABLE source_clips ADD COLUMN duration_seconds INT NULL',
    'SELECT 1'
  )
);
PREPARE stmt FROM @stmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
