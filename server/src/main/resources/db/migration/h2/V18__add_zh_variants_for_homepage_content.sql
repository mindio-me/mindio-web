-- 本文件在 db/migration/mysql/ 下有同版本号的兄弟文件
-- （mysql/V18__add_zh_variants_for_homepage_content.sql），两者必须保持同步：相同版本号、
-- 相同 schema 语义，只允许 vendor 特定的类型/语法差异。
--
-- 公开主页需要按访客语言展示不同文案。原有字段隐含为英文/默认版本，
-- 这里为首页会用到的字段各加一个中文版本，留空则前端回退到原字段。
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS title_zh VARCHAR(200);
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS bio_zh CLOB;
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS philosophy_zh CLOB;
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS availability_status_zh VARCHAR(200);
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS skills_zh CLOB;

ALTER TABLE projects ADD COLUMN IF NOT EXISTS name_zh VARCHAR(200);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS subtitle_zh VARCHAR(200);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS highlight_metric_zh VARCHAR(300);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS description_zh CLOB;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS technologies_zh CLOB;
