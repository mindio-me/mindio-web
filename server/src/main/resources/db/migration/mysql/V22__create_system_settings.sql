-- spring-boot/src/main/resources/db/migration/mysql/V22__create_system_settings.sql
-- 本文件在 db/migration/h2/ 下有同版本号的兄弟文件，两者必须保持同步。
--
-- 全站通用键值配置表，首个使用方是"在界面配置 AI 服务"。某个键没有行 = 未在界面配置，
-- 回退到环境变量；encrypted = TRUE 的值是 AES-GCM 密文。
CREATE TABLE IF NOT EXISTS system_settings (
  id BIGINT NOT NULL AUTO_INCREMENT,
  setting_key VARCHAR(100) NOT NULL,
  setting_value VARCHAR(4000),
  encrypted BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_system_settings_key UNIQUE (setting_key)
) ENGINE=InnoDB;
