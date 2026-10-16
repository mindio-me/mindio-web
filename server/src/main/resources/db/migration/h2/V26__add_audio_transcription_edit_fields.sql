-- spring-boot/src/main/resources/db/migration/h2/V26__add_audio_transcription_edit_fields.sql
-- 本文件在 db/migration/mysql/ 下有同版本号的兄弟文件，两者必须保持同步。
--
-- 录音第二阶段：场景模板（scene / scene_source）、说话人改名（speaker_names，JSON）、
-- 原文被用户修改的时间（edited_at）。逐句的 speaker 编号存在 utterances JSON 里，不加列。
-- 详见 docs/superpowers/specs/2026-10-16-recording-phase2-design.md。
ALTER TABLE audio_transcriptions ADD COLUMN IF NOT EXISTS scene VARCHAR(20);
ALTER TABLE audio_transcriptions ADD COLUMN IF NOT EXISTS scene_source VARCHAR(10);
ALTER TABLE audio_transcriptions ADD COLUMN IF NOT EXISTS speaker_names LONGTEXT;
ALTER TABLE audio_transcriptions ADD COLUMN IF NOT EXISTS edited_at DATETIME(6);
