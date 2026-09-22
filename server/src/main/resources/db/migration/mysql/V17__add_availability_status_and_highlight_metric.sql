-- spring-boot/src/main/resources/db/migration/mysql/V17__add_availability_status_and_highlight_metric.sql
--
-- 公开主页改版：Hero 需要一个可选的"当前可接洽状态"徽章；Featured Case Studies
-- 需要一条可量化的高亮说明句，随案例卡一起展示。
ALTER TABLE profiles ADD COLUMN availability_status VARCHAR(200) AFTER philosophy;
ALTER TABLE projects ADD COLUMN highlight_metric VARCHAR(300) AFTER subtitle;
