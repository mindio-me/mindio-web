-- 本文件在 db/migration/mysql/ 下有同版本号的兄弟文件
-- （mysql/V16__add_philosophy_to_profiles.sql），两者必须保持同步：相同版本号、相同 schema
-- 语义，只允许 vendor 特定的类型/语法差异。
--
-- 公开主页首页在 Hero 和 Projects 之间新增一个"理念"区块，需要一段独立于 bio
-- （已用作 Hero 副标题）的长文本。
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS philosophy CLOB;
