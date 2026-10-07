-- spring-boot/src/main/resources/db/migration/h2/V16__add_philosophy_to_profiles.sql
-- 本文件在 db/migration/mysql/ 下有同版本号的兄弟文件，两者必须保持同步。
--
-- 公开主页首页在 Hero 和 Projects 之间新增一个"理念"区块，需要一段独立于 bio
-- （已用作 Hero 副标题）的长文本。
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS philosophy CLOB;
