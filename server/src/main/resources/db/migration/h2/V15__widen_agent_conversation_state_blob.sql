-- 本文件在 db/migration/mysql/ 下有同版本号的兄弟文件
-- （mysql/V15__widen_agent_conversation_state_blob.sql），两者必须保持同步：相同版本号、
-- 相同 schema 语义，只允许 vendor 特定的类型/语法差异。
--
-- mysql/V15 把 state_blob 从 TEXT（MySQL 上限 65535 字节）放大到 LONGTEXT（上限 4GB），
-- 因为 LangGraph checkpointer 序列化后的体积会超过 TEXT 的上限。H2 的 TEXT 本就映射为
-- CLOB，没有这个字节上限，所以这里保持列类型不变，仅登记版本号以满足两个目录的版本号
-- 集合一致性校验（见 MigrationVendorParityTest）。
ALTER TABLE agent_conversation_state ALTER COLUMN state_blob SET DATA TYPE CLOB;
