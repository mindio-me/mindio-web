-- spring-boot/src/main/resources/db/migration/mysql/V13__create_note_image_refs.sql
-- 本文件在 db/migration/h2/ 下有同版本号的兄弟文件，两者必须保持同步。
--
-- 笔记→图片内容hash的引用关系，用于图片OCR完成后反查"哪些笔记引用了这份内容"，
-- 按content_hash存而不按attachment_id存——同一张图片可能通过多次独立的上传/粘贴
-- 进入不同笔记，各自对应不同的Attachment行，但共享同一个content_hash。
--
-- 这是全新建表，MySQL原生支持CREATE TABLE IF NOT EXISTS用在建表上（不像
-- ADD COLUMN IF NOT EXISTS那样需要INFORMATION_SCHEMA+PREPARE/EXECUTE迂回），
-- 所以外键约束可以直接内联在CREATE TABLE里。
CREATE TABLE IF NOT EXISTS note_image_refs (
  id BIGINT NOT NULL AUTO_INCREMENT,
  note_id BIGINT NOT NULL,
  content_hash VARCHAR(64) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_note_image_refs_note_hash (note_id, content_hash),
  KEY idx_note_image_refs_content_hash (content_hash),
  CONSTRAINT fk_note_image_refs_note FOREIGN KEY (note_id) REFERENCES notes (id)
) ENGINE=InnoDB;
