-- 本文件在 db/migration/mysql/ 下有同版本号的兄弟文件
-- （mysql/V14__add_audio_recording_clip_support.sql），两者必须保持同步。
--
-- V1 里 source_type 的 check 约束是内联匿名声明的：
--   source_type varchar(20) not null check (source_type in ('WEBPAGE', ...))
-- H2 会给它自动生成一个约束名（如 CONSTRAINT_23），这个名字取决于整份 schema 脚本里
-- 它前面建了多少张表/约束，在这份 480 多行的完整 schema 里不是能硬编码猜出来的数字。
-- 用 EXECUTE IMMEDIATE 拼出真实约束名（按 check_clause 内容匹配，而不是猜名字）再动态执行
-- DROP，这个做法已经用一个独立的 H2 2.2.224 内存库验证过，在约束名不可预测的情况下能稳定生效。
EXECUTE IMMEDIATE
    'ALTER TABLE source_clips DROP CONSTRAINT ' ||
    (SELECT cc.constraint_name FROM information_schema.check_constraints cc
     JOIN information_schema.table_constraints tc
       ON tc.constraint_name = cc.constraint_name AND tc.constraint_schema = cc.constraint_schema
     WHERE tc.table_name = 'SOURCE_CLIPS' AND tc.constraint_type = 'CHECK'
       AND cc.check_clause LIKE '%SOURCE_TYPE%' LIMIT 1);

ALTER TABLE source_clips ADD CONSTRAINT ck_source_clips_source_type
    CHECK (source_type IN ('WEBPAGE','WECHAT_ARTICLE','WECHAT_CHAT_TEXT','WECHAT_CHAT_IMAGE','AUDIO_RECORDING'));

ALTER TABLE source_clips ADD COLUMN IF NOT EXISTS duration_seconds INTEGER;
