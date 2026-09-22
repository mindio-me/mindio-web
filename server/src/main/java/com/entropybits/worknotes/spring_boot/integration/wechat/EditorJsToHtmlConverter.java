/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.integration.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class EditorJsToHtmlConverter {

    private final ObjectMapper objectMapper;

    /** 提取 EditorJS JSON 中所有 image 块的 URL */
    public List<String> extractImageUrls(String editorJsJson) throws Exception {
        List<String> urls = new ArrayList<>();
        if (editorJsJson == null || editorJsJson.isBlank()) return urls;
        JsonNode blocks = objectMapper.readTree(editorJsJson).path("blocks");
        if (!blocks.isArray()) return urls;
        for (JsonNode block : blocks) {
            if ("image".equals(block.path("type").asText())) {
                String url = block.path("data").path("file").path("url").asText("");
                if (!url.isEmpty()) urls.add(url);
            }
        }
        return urls;
    }

    /** 提取纯文本用于生成摘要（最多 maxLength 字符） */
    public String extractPlainText(String editorJsJson, int maxLength) {
        if (editorJsJson == null || editorJsJson.isBlank()) return "";
        try {
            JsonNode blocks = objectMapper.readTree(editorJsJson).path("blocks");
            StringBuilder sb = new StringBuilder();
            for (JsonNode block : blocks) {
                String type = block.path("type").asText();
                switch (type) {
                    case "paragraph":
                    case "header":
                    case "quote": {
                        String text = block.path("data").path("text").asText("")
                            .replaceAll("<[^>]*>", "").trim();
                        if (!text.isEmpty()) sb.append(text).append(" ");
                        break;
                    }
                    case "list": {
                        for (JsonNode item : block.path("data").path("items")) {
                            String text = item.isObject()
                                ? item.path("content").asText("").replaceAll("<[^>]*>", "").trim()
                                : item.asText("").replaceAll("<[^>]*>", "").trim();
                            if (!text.isEmpty()) sb.append("- ").append(text).append(" ");
                        }
                        break;
                    }
                    case "image": {
                        String url = block.path("data").path("file").path("url").asText("");
                        String caption = block.path("data").path("caption").asText("").replaceAll("<[^>]*>", "").trim();
                        if (!url.isEmpty()) {
                            sb.append("[图片: ").append(url);
                            if (!caption.isEmpty()) sb.append("，说明：").append(caption);
                            sb.append("] ");
                        }
                        break;
                    }
                    case "references": {
                        for (JsonNode item : block.path("data").path("items")) {
                            String title = item.path("title").asText("");
                            String note = item.path("note").asText("");
                            if (!title.isEmpty()) sb.append(title).append(" ");
                            if (!note.isEmpty()) sb.append(note).append(" ");
                        }
                        break;
                    }
                    case "mediaGallery": {
                        for (JsonNode item : block.path("data").path("items")) {
                            String caption = item.path("caption").asText("");
                            if (!caption.isEmpty()) sb.append(caption).append(" ");
                        }
                        break;
                    }
                    case "timeline": {
                        for (JsonNode item : block.path("data").path("items")) {
                            String date = item.path("date").asText("");
                            String title = item.path("title").asText("");
                            String description = item.path("description").asText("");
                            if (!date.isEmpty()) sb.append(date).append(" ");
                            if (!title.isEmpty()) sb.append(title).append(" ");
                            if (!description.isEmpty()) sb.append(description).append(" ");
                        }
                        break;
                    }
                    default:
                        break;
                }
                if (sb.length() >= maxLength) break;
            }
            String result = sb.toString().trim();
            return result.length() > maxLength ? result.substring(0, maxLength) : result;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 将 EditorJS JSON 转换为微信内联样式 HTML
     * @param imageUrlMapping 本地URL → 微信CDN URL 的映射
     */
    public String convert(String editorJsJson, Map<String, String> imageUrlMapping) throws Exception {
        if (editorJsJson == null || editorJsJson.isBlank()) return "";
        JsonNode blocks = objectMapper.readTree(editorJsJson).path("blocks");
        if (!blocks.isArray()) return "";

        StringBuilder html = new StringBuilder();
        for (JsonNode block : blocks) {
            html.append(convertBlock(block.path("type").asText(),
                block.path("data"), imageUrlMapping));
        }
        return html.toString();
    }

    private String convertBlock(String type, JsonNode data, Map<String, String> imgMap) {
        return switch (type) {
            case "header"      -> convertHeader(data);
            case "paragraph"   -> convertParagraph(data);
            case "image"       -> convertImage(data, imgMap);
            case "list"        -> convertList(data);
            case "code"        -> convertCode(data);
            case "quote"       -> convertQuote(data);
            case "delimiter"   -> convertDelimiter();
            case "table"       -> convertTable(data);
            case "markdown"    -> convertMarkdown(data);
            case "references"  -> convertReferences(data);
            case "mediaGallery" -> convertMediaGallery(data);
            case "timeline"    -> convertTimeline(data);
            default -> "";
        };
    }

    private String convertHeader(JsonNode data) {
        String text = data.path("text").asText("");
        int level = data.path("level").asInt(2);
        if (level == 1) {
            // H1: 左侧粗色条 + 浅蓝背景 + 深色文字
            return String.format(
                "<section style=\"margin:24px 0 14px;\">"
                + "<h1 style=\"font-size:20px;font-weight:bold;color:#1a2f5a;"
                + "background:#eef3fb;border-left:5px solid #2b5fad;"
                + "padding:10px 16px;margin:0;line-height:1.6;\">%s</h1>"
                + "</section>", text);
        } else if (level == 2) {
            // H2: 蓝色文字 + 底部细线
            return String.format(
                "<section style=\"margin:20px 0 10px;\">"
                + "<h2 style=\"font-size:18px;font-weight:bold;color:#2b5fad;"
                + "border-bottom:2px solid #2b5fad;padding:0 0 6px 0;"
                + "margin:0;line-height:1.6;\">%s</h2>"
                + "</section>", text);
        } else {
            // H3: 左侧细色条 + 深灰文字
            return String.format(
                "<section style=\"margin:16px 0 8px;\">"
                + "<h3 style=\"font-size:16px;font-weight:bold;color:#333;"
                + "border-left:3px solid #4a7fd4;padding:4px 0 4px 12px;"
                + "margin:0;line-height:1.6;\">%s</h3>"
                + "</section>", text);
        }
    }

    private String convertParagraph(JsonNode data) {
        String text = data.path("text").asText("");
        if (text.isEmpty()) return "";
        return String.format(
            "<p style=\"font-size:16px;color:#333;line-height:1.8;margin:12px 0;"
            + "word-wrap:break-word;\">%s</p>", text);
    }

    private String convertImage(JsonNode data, Map<String, String> imgMap) {
        String localUrl = data.path("file").path("url").asText("");
        String src = imgMap.getOrDefault(localUrl, localUrl);
        String caption = data.path("caption").asText("");
        StringBuilder sb = new StringBuilder();
        sb.append("<section style=\"text-align:center;margin:16px 0;\">")
          .append(String.format("<img src=\"%s\" style=\"max-width:100%%;height:auto;"
              + "border-radius:4px;\" />", src));
        if (!caption.isEmpty()) {
            sb.append(String.format(
                "<p style=\"font-size:13px;color:#888;margin:6px 0 0;text-align:center;"
                + "\">%s</p>", caption));
        }
        return sb.append("</section>").toString();
    }

    private String convertList(JsonNode data) {
        boolean ordered = "ordered".equals(data.path("style").asText("unordered"));
        String tag = ordered ? "ol" : "ul";
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("<%s style=\"margin:12px 0;padding-left:20px;color:#333;"
            + "font-size:16px;line-height:1.8;\">", tag));
        for (JsonNode item : data.path("items")) {
            String text = item.isTextual() ? item.asText() : item.path("content").asText();
            sb.append(String.format("<li style=\"margin:4px 0;\">%s</li>", text));
        }
        return sb.append(String.format("</%s>", tag)).toString();
    }

    private String convertCode(JsonNode data) {
        String code = data.path("code").asText("")
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\n", "<br>");
        return String.format(
            "<section style=\"margin:16px 0;\"><pre style=\"background:#1e1e1e;color:#d4d4d4;"
            + "padding:16px;border-radius:6px;font-family:Consolas,Monaco,monospace;"
            + "font-size:14px;overflow-x:auto;white-space:pre-wrap;word-wrap:break-word;"
            + "line-height:1.6;\">%s</pre></section>", code);
    }

    private String convertQuote(JsonNode data) {
        String text = data.path("text").asText("");
        String caption = data.path("caption").asText("");
        StringBuilder sb = new StringBuilder();
        sb.append("<section style=\"margin:16px 0;padding:12px 16px;background:#f8f4f0;"
            + "border-left:4px solid #e6622e;\">")
          .append(String.format("<p style=\"font-size:16px;color:#555;font-style:italic;"
              + "margin:0;line-height:1.8;\">%s</p>", text));
        if (!caption.isEmpty()) {
            sb.append(String.format("<p style=\"font-size:13px;color:#888;margin:6px 0 0;"
                + "\">— %s</p>", caption));
        }
        return sb.append("</section>").toString();
    }

    private String convertDelimiter() {
        return "<p style=\"text-align:center;color:#888;font-size:18px;margin:20px 0;"
            + "letter-spacing:8px;\">* * *</p>";
    }

    private String convertMarkdown(JsonNode data) {
        String md = data.path("markdown").asText("");
        if (md.isBlank()) return "";

        String h = md;
        // Code blocks (multiline, before inline code)
        h = h.replaceAll("(?s)```\\w*\\n(.*?)```",
            "<pre style=\"background:#1e1e1e;color:#d4d4d4;padding:16px;border-radius:6px;"
            + "font-family:Consolas,Monaco,monospace;font-size:14px;overflow-x:auto;"
            + "white-space:pre-wrap;word-wrap:break-word;line-height:1.6;\">$1</pre>");
        // Inline code
        h = h.replaceAll("`([^`\\n]+)`",
            "<code style=\"background:#f0f0f0;padding:2px 6px;border-radius:3px;"
            + "font-family:Consolas,Monaco,monospace;font-size:13px;\">$1</code>");
        // Images (before links)
        h = h.replaceAll("!\\[([^\\]]*)\\]\\(([^)]+)\\)",
            "<img src=\"$2\" alt=\"$1\" style=\"max-width:100%;height:auto;"
            + "border-radius:4px;margin:8px 0;\" />");
        // Headings
        h = h.replaceAll("(?m)^#### (.*)$",
            "<h4 style=\"font-size:15px;font-weight:bold;color:#333;margin:10px 0;\">$1</h4>");
        h = h.replaceAll("(?m)^### (.*)$",
            "<h3 style=\"font-size:16px;font-weight:bold;color:#333;margin:12px 0;\">$1</h3>");
        h = h.replaceAll("(?m)^## (.*)$",
            "<h2 style=\"font-size:18px;font-weight:bold;color:#333;margin:14px 0;\">$1</h2>");
        h = h.replaceAll("(?m)^# (.*)$",
            "<h1 style=\"font-size:20px;font-weight:bold;color:#333;margin:16px 0;\">$1</h1>");
        // Blockquotes
        h = h.replaceAll("(?m)^> (.*)$",
            "<blockquote style=\"border-left:4px solid #e6622e;padding:8px 16px;"
            + "background:#f8f4f0;color:#555;margin:12px 0;font-style:italic;\">$1</blockquote>");
        // Bold & italic
        h = h.replaceAll("\\*\\*([^*\\n]+)\\*\\*", "<strong>$1</strong>");
        h = h.replaceAll("\\*([^*\\n]+)\\*", "<em>$1</em>");
        // Links — [text](url)  →  <a href="url">text</a>
        h = h.replaceAll("\\[([^\\]\\n]+)\\]\\(([^)\\n]+)\\)", "<a href=\"$2\">$1</a>");
        // Unordered list items
        h = h.replaceAll("(?m)^- (.*)$",
            "<li style=\"margin:4px 0;color:#333;font-size:16px;line-height:1.8;\">$1</li>");
        // Horizontal rule
        h = h.replaceAll("(?m)^---$",
            "<hr style=\"border:none;border-top:1px solid #ddd;margin:16px 0;\" />");
        // Newlines → <br>
        h = h.replace("\n", "<br>");
        // Wrap consecutive <li> in <ul> (remove <br> between items first)
        h = h.replaceAll("(</li>)<br>(<li)", "$1$2");
        h = h.replaceAll("(<li[^>]*>.*?</li>)+",
            "<ul style=\"margin:12px 0;padding-left:20px;\">" + "$0" + "</ul>");

        return String.format(
            "<section style=\"font-size:16px;color:#333;line-height:1.8;margin:12px 0;"
            + "word-wrap:break-word;\">%s</section>", h);
    }

    private String convertTable(JsonNode data) {
        boolean withHeadings = data.path("withHeadings").asBoolean(false);
        StringBuilder sb = new StringBuilder();
        sb.append("<table style=\"border-collapse:collapse;width:100%;margin:16px 0;"
            + "font-size:15px;color:#333;\">");
        boolean firstRow = true;
        for (JsonNode row : data.path("content")) {
            sb.append("<tr>");
            for (JsonNode cell : row) {
                if (firstRow && withHeadings) {
                    sb.append(String.format("<th style=\"border:1px solid #ddd;padding:8px 12px;"
                        + "background:#2b5fad;color:#fff;font-weight:bold;text-align:left;"
                        + "\">%s</th>", cell.asText()));
                } else {
                    sb.append(String.format("<td style=\"border:1px solid #ddd;padding:8px 12px;"
                        + "background:#fff;\">%s</td>", cell.asText()));
                }
            }
            sb.append("</tr>");
            firstRow = false;
        }
        return sb.append("</table>").toString();
    }

    private String convertReferences(JsonNode data) {
        StringBuilder sb = new StringBuilder();
        sb.append("<ul style=\"margin:12px 0;padding-left:20px;color:#333;font-size:16px;line-height:1.8;\">");
        for (JsonNode item : data.path("items")) {
            String kind = item.path("kind").asText("link");
            String title = escapeHtml(item.path("title").asText(""));
            String url = "note".equals(kind) ? "" : item.path("url").asText("");
            String note = escapeHtml(item.path("note").asText(""));
            sb.append("<li style=\"margin:4px 0;\">");
            if (!url.isEmpty()) {
                sb.append(String.format("<a href=\"%s\" style=\"color:#2b5fad;\">%s</a>", url, title));
            } else {
                sb.append(title);
            }
            if (!note.isEmpty()) {
                sb.append(String.format(" <span style=\"color:#888;font-size:13px;\">（%s）</span>", note));
            }
            sb.append("</li>");
        }
        return sb.append("</ul>").toString();
    }

    private String convertMediaGallery(JsonNode data) {
        StringBuilder sb = new StringBuilder();
        sb.append("<section style=\"display:flex;flex-wrap:wrap;gap:8px;margin:16px 0;\">");
        for (JsonNode item : data.path("items")) {
            String type = item.path("type").asText("image");
            String caption = escapeHtml(item.path("caption").asText(""));
            sb.append("<figure style=\"margin:0;width:200px;\">");
            if ("video".equals(type)) {
                String embedUrl = item.path("embedUrl").asText("");
                sb.append(String.format(
                    "<iframe src=\"%s\" style=\"width:100%%;height:120px;border:0;\" allowfullscreen></iframe>",
                    embedUrl));
            } else if ("audio".equals(type)) {
                String url = item.path("url").asText("");
                sb.append(String.format("<audio controls src=\"%s\" style=\"width:100%%;\"></audio>", url));
            } else {
                String url = item.path("url").asText("");
                sb.append(String.format(
                    "<img src=\"%s\" style=\"width:100%%;height:auto;border-radius:4px;\" />", url));
            }
            if (!caption.isEmpty()) {
                sb.append(String.format(
                    "<figcaption style=\"font-size:12px;color:#888;text-align:center;\">%s</figcaption>", caption));
            }
            sb.append("</figure>");
        }
        return sb.append("</section>").toString();
    }

    private String convertTimeline(JsonNode data) {
        StringBuilder sb = new StringBuilder();
        sb.append("<section style=\"margin:16px 0;border-left:2px solid #ddd;padding-left:16px;\">");
        for (JsonNode item : data.path("items")) {
            String date = escapeHtml(item.path("date").asText(""));
            String title = escapeHtml(item.path("title").asText(""));
            String description = escapeHtml(item.path("description").asText(""));
            String link = item.path("link").asText("");
            sb.append("<div style=\"margin-bottom:12px;\">")
              .append(String.format("<div style=\"font-size:13px;color:#888;\">%s</div>", date))
              .append("<div style=\"font-size:16px;color:#333;font-weight:bold;\">");
            sb.append(title);
            if (!link.isEmpty()) {
                sb.append(String.format(" <a href=\"%s\" style=\"color:#2b5fad;font-size:14px;text-decoration:none;\">🔗</a>", link));
            }
            sb.append("</div>");
            if (!description.isEmpty()) {
                sb.append(String.format("<div style=\"font-size:14px;color:#555;\">%s</div>", description));
            }
            sb.append("</div>");
        }
        return sb.append("</section>").toString();
    }

    /**
     * 转义 HTML 特殊字符，防止 references/mediaGallery/timeline 中的用户输入文本
     * （title/note/caption/date/description）在导出的 HTML（个人主页 / 微信文章）中造成存储型 XSS。
     * 注意：仅用于文本字段，不用于 href/src 等 URL 属性值。
     */
    private static String escapeHtml(String input) {
        if (input == null || input.isEmpty()) return input;
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
