-- spring-boot/src/main/resources/db/migration/h2/V11__drop_clip_search_messages.sql
-- 本文件在 db/migration/mysql/ 下有同版本号的兄弟文件，两者必须保持同步。
-- ClipSearchService 这套"AI搜索资料"面板已经并入独立的LangGraph Agent服务
-- （search_web 工具），旧数据不再需要，见
-- docs/superpowers/specs/2026-09-04-note-research-workflow-design.md。
DROP TABLE IF EXISTS clip_search_messages;
