-- spring-boot/src/main/resources/db/migration/mysql/V18__add_zh_variants_for_homepage_content.sql
--
-- 公开主页需要按访客语言展示不同文案。原有字段隐含为英文/默认版本，
-- 这里为首页会用到的字段各加一个中文版本，留空则前端回退到原字段。
ALTER TABLE profiles ADD COLUMN title_zh VARCHAR(200) AFTER title;
ALTER TABLE profiles ADD COLUMN bio_zh LONGTEXT AFTER bio;
ALTER TABLE profiles ADD COLUMN philosophy_zh LONGTEXT AFTER philosophy;
ALTER TABLE profiles ADD COLUMN availability_status_zh VARCHAR(200) AFTER availability_status;
ALTER TABLE profiles ADD COLUMN skills_zh LONGTEXT AFTER skills;

ALTER TABLE projects ADD COLUMN name_zh VARCHAR(200) AFTER name;
ALTER TABLE projects ADD COLUMN subtitle_zh VARCHAR(200) AFTER subtitle;
ALTER TABLE projects ADD COLUMN highlight_metric_zh VARCHAR(300) AFTER highlight_metric;
ALTER TABLE projects ADD COLUMN description_zh LONGTEXT AFTER description;
ALTER TABLE projects ADD COLUMN technologies_zh LONGTEXT AFTER technologies;
