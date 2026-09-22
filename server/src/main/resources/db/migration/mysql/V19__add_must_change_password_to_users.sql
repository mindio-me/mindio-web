-- spring-boot/src/main/resources/db/migration/mysql/V19__add_must_change_password_to_users.sql
--
-- 自用 web 版下线了注册功能，改为首次启动自建默认管理员账号（见 DataInitializer 的
-- prod-only CommandLineRunner）。这个字段标记该账号是否仍在用初始密码，前端据此在
-- 登录后强制跳转改密。
ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE AFTER created_at;
