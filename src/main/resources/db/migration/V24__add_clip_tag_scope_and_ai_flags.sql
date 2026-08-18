-- Tag 作用范围标记：区分一个标签历史上是否被笔记/收藏使用过或主动创建
ALTER TABLE tags
    ADD COLUMN used_by_notes BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN used_by_clips BOOLEAN NOT NULL DEFAULT FALSE;

-- 回填：按旧关联表推导历史使用范围（必须在下面重建 source_clip_tags 之前，用旧表数据算）
UPDATE tags t SET used_by_notes = EXISTS (
    SELECT 1 FROM note_tags nt WHERE nt.tag_id = t.id
);
UPDATE tags t SET used_by_clips = EXISTS (
    SELECT 1 FROM source_clip_tags sct WHERE sct.tag_id = t.id
);

-- source_clip_tags 升级为带来源标记的关联实体 clip_tag_links
CREATE TABLE clip_tag_links (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    clip_id        BIGINT      NOT NULL,
    tag_id         BIGINT      NOT NULL,
    manually_added BOOLEAN     NOT NULL DEFAULT FALSE,
    ai_suggested   BOOLEAN     NOT NULL DEFAULT FALSE,
    linked_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_clip_tag (clip_id, tag_id),
    KEY idx_ctl_tag (tag_id),
    CONSTRAINT fk_ctl_clip FOREIGN KEY (clip_id) REFERENCES source_clips (id) ON DELETE CASCADE,
    CONSTRAINT fk_ctl_tag  FOREIGN KEY (tag_id)  REFERENCES tags (id) ON DELETE CASCADE
);

-- 现有关联全部来自人工/导入流程，还没有产生过 AI 分类关联
INSERT INTO clip_tag_links (clip_id, tag_id, manually_added, ai_suggested)
SELECT clip_id, tag_id, TRUE, FALSE FROM source_clip_tags;

DROP TABLE source_clip_tags;

-- 收藏是否被用户手动调整过标签：调整过之后，知识地图重新生成不再覆盖
ALTER TABLE source_clips
    ADD COLUMN tags_manually_adjusted BOOLEAN NOT NULL DEFAULT FALSE;
