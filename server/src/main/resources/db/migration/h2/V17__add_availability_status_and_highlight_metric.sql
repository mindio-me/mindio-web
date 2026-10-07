-- spring-boot/src/main/resources/db/migration/h2/V17__add_availability_status_and_highlight_metric.sql
-- 本文件在 db/migration/mysql/ 下有同版本号的兄弟文件，两者必须保持同步。
--
-- 公开主页改版：Hero 需要一个可选的"当前可接洽状态"徽章；Featured Case Studies
-- 需要一条可量化的高亮说明句，随案例卡一起展示。
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS availability_status VARCHAR(200);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS highlight_metric VARCHAR(300);
