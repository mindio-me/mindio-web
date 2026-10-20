/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service.sharecard;

import com.entropybits.worknotes.spring_boot.entity.Note;
import org.springframework.web.util.HtmlUtils;

import java.net.URI;

/**
 * 渲染给链接预览爬虫（X / LinkedIn / Reddit）读取的 Open Graph meta 页。
 */
public final class SharePageRenderer {

    private SharePageRenderer() {
    }

    public static String render(Note note, String publicBase) {
        String title = notBlank(note.getShareCardTitle()) ? note.getShareCardTitle().trim()
                : (note.getTitle() == null ? "" : note.getTitle());
        String description = notBlank(note.getSummary()) ? note.getSummary().trim()
                : NotePlainText.extract(note, 150);
        if (!notBlank(description)) {
            // 全是视频/图片的笔记抽不出正文；空描述会让部分平台（LinkedIn 告警）处理异常，用标题兜底
            description = title;
        }
        String pageUrl = publicBase + "/notes/" + note.getId();
        String image = notBlank(note.getShareCardImageUrl()) ? toPublicUrl(note.getShareCardImageUrl().trim(), publicBase) : null;

        String t = esc(title);
        String d = esc(description);
        String u = esc(pageUrl);
        StringBuilder sb = new StringBuilder(1024);
        sb.append("<!doctype html><html><head><meta charset=\"utf-8\">");
        sb.append("<title>").append(t).append("</title>");
        meta(sb, "og:type", "article");
        meta(sb, "og:title", t);
        meta(sb, "og:description", d);
        meta(sb, "og:url", u);
        sb.append("<link rel=\"canonical\" href=\"").append(u).append("\">");
        named(sb, "twitter:card", image != null ? "summary_large_image" : "summary");
        named(sb, "twitter:title", t);
        named(sb, "twitter:description", d);
        if (image != null) {
            String i = esc(image);
            meta(sb, "og:image", i);
            meta(sb, "og:image:width", "1200");
            meta(sb, "og:image:height", "630");
            named(sb, "twitter:image", i);
        }
        sb.append("</head><body><a href=\"").append(u).append("\">").append(t).append("</a></body></html>");
        return sb.toString();
    }

    /** 只保留 path(+query)，主机一律换成 publicBase，避免泄露库里存的内网/本地主机。 */
    static String toPublicUrl(String stored, String publicBase) {
        String path = stored;
        try {
            URI uri = URI.create(stored);
            if (uri.getRawPath() != null && (uri.isAbsolute() || stored.startsWith("//"))) {
                path = uri.getRawPath() + (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "");
            }
        } catch (IllegalArgumentException ignored) {
            // 保持原样
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return publicBase + path;
    }

    private static void meta(StringBuilder sb, String property, String escaped) {
        sb.append("<meta property=\"").append(property).append("\" content=\"").append(escaped).append("\">");
    }

    private static void named(StringBuilder sb, String name, String escaped) {
        sb.append("<meta name=\"").append(name).append("\" content=\"").append(escaped).append("\">");
    }

    private static String esc(String s) {
        // 带编码参数时只转义 < > & " '，中文和破折号等原样输出（不带时会转成 &mdash; 这类实体名）
        return HtmlUtils.htmlEscape(s == null ? "" : s, "UTF-8");
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
