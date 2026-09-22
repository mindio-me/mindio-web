-- spring-boot/src/main/resources/db/migration/mysql/V16__add_philosophy_to_profiles.sql
--
-- 公开主页首页在 Hero 和 Projects 之间新增一个"理念"区块，需要一段独立于 bio
-- （已用作 Hero 副标题）的长文本。
ALTER TABLE profiles ADD COLUMN philosophy LONGTEXT AFTER bio;
