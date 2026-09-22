-- 本文件在 db/migration/h2/ 下有同版本号的兄弟文件（h2/V2__reconcile_known_ddl_auto_drift.sql），
-- 两者必须保持同步：相同版本号、相同 schema 语义，只允许 vendor 特定的类型/语法差异。
-- 补齐 ddl-auto:update 曾经静默失败、没能加到已有 tags 表上的两个 NOT NULL 列。
-- 对全新安装（V1 已经建好这两列）和老库（V1 被 baseline 跳过，从未真正建过这两列）都要安全：
-- ADD COLUMN IF NOT EXISTS 对已存在的列是no-op，对缺失的列会用 DEFAULT FALSE 补上。
ALTER TABLE tags ADD COLUMN IF NOT EXISTS used_by_notes BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tags ADD COLUMN IF NOT EXISTS used_by_clips BOOLEAN NOT NULL DEFAULT FALSE;
