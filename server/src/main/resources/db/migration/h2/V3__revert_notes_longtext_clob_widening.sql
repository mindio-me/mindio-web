-- 撤销旧版 H2LongTextFixConfig（desktop CommandLineRunner，已删除）对 notes.content / notes.summary
-- 做的 CLOB 收窄改动。该 runner 的原始假设——H2（MODE=MySQL）把 LONGTEXT/TEXT 建成固定长度的
-- CHARACTER(32767)——已实测证实为假：H2 2.2.224 实际上把 LONGTEXT/TEXT 建成
-- character varying(1000000000)，本来就足够大，不需要额外加宽。
--
-- 已经跑过旧 runner 的存量 desktop 库，notes.content / notes.summary 此刻是真正的 CLOB 类型，
-- 与 Note.java 的 columnDefinition = "LONGTEXT" / "TEXT" 声明（Hibernate 校验期望
-- character varying）不一致，新加入的 ddl-auto: validate 会在下一次启动时报
-- Schema-validation: wrong column type 并拒绝启动。这里统一把两列改回 VARCHAR(1000000000)。
--
-- 已实测验证：(a) 对从未跑过旧 runner、V1 就已经是 VARCHAR(1000000000) 的全新安装库，
-- 这两条 ALTER 是安全的 no-op；(b) 对已被旧 runner 改成 CLOB 的存量库，ALTER 之后类型与
-- 全新安装一致，且原有行数据（含多字节 Unicode 内容）读回后逐字节相同，没有数据丢失。
--
-- 只影响 db/migration/h2/ ——旧 runner 只在 desktop（H2）profile 下生效，MySQL 自建库从未
-- 出现过这个问题，因此不需要对应的 mysql/V3 文件。
ALTER TABLE notes ALTER COLUMN content VARCHAR(1000000000);
ALTER TABLE notes ALTER COLUMN summary VARCHAR(1000000000);
