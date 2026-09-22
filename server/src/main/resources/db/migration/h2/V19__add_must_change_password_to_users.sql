-- spring-boot/src/main/resources/db/migration/h2/V19__add_must_change_password_to_users.sql
--
-- 兄弟文件：mysql/V19__add_must_change_password_to_users.sql（同一变更，H2/MySQL 语法分叉）。
-- 桌面版走独立的许可证登录（DesktopAuthController），不会创建 mustChangePassword=true 的账号，
-- 但字段仍需存在以匹配 JPA 实体映射。
ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE AFTER created_at;
