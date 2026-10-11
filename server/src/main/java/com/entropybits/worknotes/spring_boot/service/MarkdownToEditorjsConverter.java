/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.commonmark.ext.footnotes.FootnoteDefinition;
import org.commonmark.ext.footnotes.FootnoteReference;
import org.commonmark.ext.footnotes.FootnotesExtension;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.*;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 转 Editor.js 块：agent 写笔记、Markdown 笔记"转为 editor.js 文档"共用。能映射成原生块的
 * 一律用原生块，Markdown 块只兜底（mermaid、原始 HTML、单个节点转换出错）。整篇一次解析：脚注引用与
 * 定义、跨空行的松散列表都需要全文上下文；Obsidian/Typora 方言先由 MarkdownSourceNormalizer 归一化。
 * 行内只输出编辑器认识的 b/i/s/mark/code/a/sup，其余文字全部 HTML 转义（编辑器用 innerHTML 渲染，
 * 模型输出可能被网页内容注入）。脚注编号等状态放在每次 convert 新建的 Conversion 里：本类被
 * service 单例并发调用。
 */
final class MarkdownToEditorjsConverter {

    static final String MISSING_IMAGE_PREFIX = "⚠ 图片未导入：";
    static final String FOOTNOTES_TITLE = "脚注";

    private static final Parser PARSER = Parser.builder()
            .extensions(List.of(
                    TablesExtension.create(),
                    StrikethroughExtension.builder().requireTwoTildes(true).build(),
                    FootnotesExtension.create()))
            .includeSourceSpans(IncludeSourceSpans.BLOCKS)
            .build();
    private static final Pattern TASK_PREFIX = Pattern.compile("^\\[([ xX])\\]\\s+");
    private static final Pattern CALLOUT = Pattern.compile("^\\[!([A-Za-z][\\w-]*)\\][+-]?\\s*(.*)$");
    /** 行内公式 $x$（内侧紧贴非空白，避免把"$5 和 $10"当公式）或高亮 ==x== */
    private static final Pattern INLINE_SPECIAL =
            Pattern.compile("\\$(?![\\s$])([^$\\n]+?)(?<!\\s)\\$|==(?=\\S)(.+?)(?<=\\S)==");
    private static final Pattern EDGE_BREAKS = Pattern.compile("^(?:\\s|<br>)+|(?:\\s|<br>)+$");

    private final ObjectMapper om;

    MarkdownToEditorjsConverter(ObjectMapper om) {
        this.om = om;
    }

    List<ObjectNode> convert(String markdown) {
        if (markdown == null || markdown.isBlank()) return new ArrayList<>();
        return new Conversion(MarkdownSourceNormalizer.normalize(markdown)).run();
    }

    /** 节点在源码里对应的原文（块级 source span 按行拼回），单个节点转换出错时整段放进 Markdown 块 */
    static String sourceOf(Node node, String[] lines) {
        StringBuilder sb = new StringBuilder();
        for (SourceSpan span : node.getSourceSpans()) {
            if (span.getLineIndex() >= lines.length) continue;
            String line = lines[span.getLineIndex()];
            int from = Math.min(span.getColumnIndex(), line.length());
            int to = Math.min(from + span.getLength(), line.length());
            if (sb.length() > 0) sb.append('\n');
            sb.append(line, from, to);
        }
        return sb.toString();
    }

    /** 导入只上传了 .md 文字：只有外链和站内根相对地址（agent 写的 /api/uploads/...）是真能显示的图片 */
    static boolean isImportedImage(String src) {
        if (src == null) return false;
        String s = src.trim().toLowerCase(Locale.ROOT);
        return s.startsWith("http://") || s.startsWith("https://") || s.startsWith("/");
    }

    /** 一段连续纯文本：转义后识别行内公式和高亮 */
    static String renderText(String raw) {
        StringBuilder sb = new StringBuilder();
        Matcher m = INLINE_SPECIAL.matcher(raw);
        int last = 0;
        while (m.find()) {
            sb.append(AgentHtmlImageEditor.escape(raw.substring(last, m.start())));
            if (m.group(1) != null) {
                sb.append("<code class=\"inline-code\">$").append(AgentHtmlImageEditor.escape(m.group(1)))
                        .append("$</code>");
            } else {
                sb.append("<mark class=\"cdx-marker\">").append(AgentHtmlImageEditor.escape(m.group(2)))
                        .append("</mark>");
            }
            last = m.end();
        }
        sb.append(AgentHtmlImageEditor.escape(raw.substring(last)));
        return sb.toString();
    }

    /**
     * 链接只放行 http/https/mailto 和不带协议的相对地址：模型输出可能被网页内容注入，
     * javascript: 之类的链接写进笔记后，公开页用 v-html 渲染，点一下就在本站执行脚本。
     */
    static boolean isSafeHref(String href) {
        if (href == null) return false;
        String h = href.trim().toLowerCase(Locale.ROOT);
        int colon = h.indexOf(':');
        int firstDelimiter = h.length();
        for (char d : new char[]{'/', '?', '#'}) {
            int i = h.indexOf(d);
            if (i >= 0 && i < firstDelimiter) firstDelimiter = i;
        }
        if (colon < 0 || colon > firstDelimiter) return true;
        return h.startsWith("http:") || h.startsWith("https:") || h.startsWith("mailto:");
    }

    private static String plainText(Node node) {
        StringBuilder sb = new StringBuilder();
        for (Node c = node.getFirstChild(); c != null; c = c.getNext()) {
            if (c instanceof Text t) sb.append(t.getLiteral());
            else sb.append(plainText(c));
        }
        return sb.toString();
    }

    private static void addIfNotBlank(List<String> parts, String html) {
        if (!html.isBlank()) parts.add(html);
    }

    private final class Conversion {

        private final String source;
        private final List<ObjectNode> blocks = new ArrayList<>();
        /** 小写 label → 编号；插入顺序即编号顺序 */
        private final Map<String, Integer> footnoteNumbers = new LinkedHashMap<>();
        private final Map<String, FootnoteDefinition> footnoteDefs = new LinkedHashMap<>();

        Conversion(String source) {
            this.source = source;
        }

        List<ObjectNode> run() {
            Node doc = PARSER.parse(source);
            String[] lines = source.split("\n", -1);
            for (Node n = doc.getFirstChild(); n != null; n = n.getNext()) {
                if (n instanceof FootnoteDefinition def) {
                    footnoteDefs.putIfAbsent(def.getLabel().toLowerCase(Locale.ROOT), def);
                    continue;
                }
                int before = blocks.size();
                try {
                    addBlock(n);
                } catch (RuntimeException e) {
                    blocks.subList(before, blocks.size()).clear();
                    blocks.add(markdownBlock(sourceOf(n, lines)));
                }
            }
            int beforeFootnotes = blocks.size();
            try {
                appendFootnotes();
            } catch (RuntimeException e) {
                // 整篇转换不失败：脚注区出错就整段不要，正文块保留
                blocks.subList(beforeFootnotes, blocks.size()).clear();
            }
            return blocks;
        }

        private void addBlock(Node n) {
            if (n instanceof Heading h) {
                ObjectNode data = om.createObjectNode();
                data.put("text", inline(h));
                data.put("level", Math.max(1, Math.min(6, h.getLevel())));
                blocks.add(block("header", data));
            } else if (n instanceof Paragraph p) {
                addParagraph(p);
            } else if (n instanceof BulletList || n instanceof OrderedList) {
                blocks.add(isTaskList(n) ? checklist(n) : list(n));
            } else if (n instanceof FencedCodeBlock code) {
                blocks.add(fenced(code));
            } else if (n instanceof IndentedCodeBlock code) {
                blocks.add(code(code.getLiteral()));
            } else if (n instanceof BlockQuote q) {
                ObjectNode callout = callout(q);
                blocks.add(callout != null ? callout : quote(q));
            } else if (n instanceof ThematicBreak) {
                blocks.add(block("delimiter", om.createObjectNode()));
            } else if (n instanceof HtmlBlock html) {
                blocks.add(markdownBlock(html.getLiteral().trim()));
            } else if (n instanceof TableBlock table) {
                blocks.add(table(table));
            } else {
                addParagraphHtml(inline(n));
            }
        }

        /** 段落里夹着的图片拆成独立块：文字 / 图片 / 文字 */
        private void addParagraph(Paragraph p) {
            Node runStart = p.getFirstChild();
            for (Node c = p.getFirstChild(); c != null; c = c.getNext()) {
                if (c instanceof Image img) {
                    addParagraphHtml(inlineRange(runStart, c));
                    blocks.add(imageOrPlaceholder(img));
                    runStart = c.getNext();
                }
            }
            addParagraphHtml(inlineRange(runStart, null));
        }

        private void addParagraphHtml(String html) {
            String text = EDGE_BREAKS.matcher(html).replaceAll("");
            if (text.isEmpty()) return;
            ObjectNode data = om.createObjectNode();
            data.put("text", text);
            blocks.add(block("paragraph", data));
        }

        private ObjectNode imageOrPlaceholder(Image img) {
            if (isImportedImage(img.getDestination())) return image(img);
            ObjectNode data = om.createObjectNode();
            data.put("text", AgentHtmlImageEditor.escape(MISSING_IMAGE_PREFIX + img.getDestination()));
            return block("paragraph", data);
        }

        private ObjectNode fenced(FencedCodeBlock code) {
            String info = code.getInfo() == null ? "" : code.getInfo().trim();
            String lang = info.isEmpty() ? "" : info.split("\\s+")[0].toLowerCase(Locale.ROOT);
            if ("math".equals(lang)) {
                ObjectNode data = om.createObjectNode();
                data.put("tex", stripTrailingNewline(code.getLiteral()));
                return block("math", data);
            }
            if ("mermaid".equals(lang)) {
                return markdownBlock("```mermaid\n" + stripTrailingNewline(code.getLiteral()) + "\n```");
            }
            return code(code.getLiteral());
        }

        private ObjectNode quote(BlockQuote q) {
            List<String> parts = new ArrayList<>();
            for (Node c = q.getFirstChild(); c != null; c = c.getNext()) parts.add(inline(c));
            ObjectNode data = om.createObjectNode();
            data.put("text", String.join("<br>", parts));
            data.put("caption", "");
            data.put("alignment", "left");
            return block("quote", data);
        }

        /** Obsidian callout：> [!type] 标题 + 正文 → warning 块；不是 callout 返回 null */
        private ObjectNode callout(BlockQuote q) {
            if (!(q.getFirstChild() instanceof Paragraph p)) return null;
            Node lineEnd = p.getFirstChild();
            while (lineEnd != null && !(lineEnd instanceof SoftLineBreak) && !(lineEnd instanceof HardLineBreak)) {
                lineEnd = lineEnd.getNext();
            }
            StringBuilder head = new StringBuilder();
            for (Node c = p.getFirstChild(); c != lineEnd; c = c.getNext()) {
                head.append(c instanceof Text t ? t.getLiteral() : plainText(c));
            }
            Matcher m = CALLOUT.matcher(head.toString().trim());
            if (!m.matches()) return null;
            List<String> parts = new ArrayList<>();
            if (lineEnd != null) addIfNotBlank(parts, inlineRange(lineEnd.getNext(), null));
            for (Node c = p.getNext(); c != null; c = c.getNext()) addIfNotBlank(parts, inline(c));
            String type = m.group(1);
            String title = m.group(2).isBlank()
                    ? Character.toUpperCase(type.charAt(0)) + type.substring(1).toLowerCase(Locale.ROOT)
                    : m.group(2).trim();
            ObjectNode data = om.createObjectNode();
            data.put("title", AgentHtmlImageEditor.escape(title));
            data.put("message", String.join("<br>", parts));
            return block("warning", data);
        }

        private ObjectNode table(TableBlock t) {
            ObjectNode data = om.createObjectNode();
            data.put("withHeadings", t.getFirstChild() instanceof TableHead);
            data.put("stretched", false);
            ArrayNode content = data.putArray("content");
            for (Node section = t.getFirstChild(); section != null; section = section.getNext()) {
                for (Node row = section.getFirstChild(); row != null; row = row.getNext()) {
                    ArrayNode cells = content.addArray();
                    for (Node cell = row.getFirstChild(); cell != null; cell = cell.getNext()) cells.add(inline(cell));
                }
            }
            return block("table", data);
        }

        private ObjectNode list(Node listNode) {
            boolean ordered = listNode instanceof OrderedList;
            ObjectNode data = om.createObjectNode();
            data.put("style", ordered ? "ordered" : "unordered");
            ObjectNode meta = data.putObject("meta");
            if (ordered) meta.put("counterType", "numeric");
            data.set("items", listItems(listNode));
            return block("list", data);
        }

        private ArrayNode listItems(Node listNode) {
            ArrayNode items = om.createArrayNode();
            for (Node li = listNode.getFirstChild(); li != null; li = li.getNext()) {
                ObjectNode item = items.addObject();
                String content = "";
                ArrayNode nested = om.createArrayNode();
                for (Node c = li.getFirstChild(); c != null; c = c.getNext()) {
                    if (c instanceof BulletList || c instanceof OrderedList) nested.addAll(listItems(c));
                    else if (content.isEmpty()) content = inline(c);
                }
                item.put("content", content);
                item.putObject("meta");
                item.set("items", nested);
            }
            return items;
        }

        /** 用整段纯文本判断：commonmark 可能把 "[x]" 拆成多个 Text 节点，只看第一个节点会漏判 */
        private boolean isTaskList(Node listNode) {
            if (!(listNode instanceof BulletList) || listNode.getFirstChild() == null) return false;
            for (Node li = listNode.getFirstChild(); li != null; li = li.getNext()) {
                if (!(li.getFirstChild() instanceof Paragraph p) || !TASK_PREFIX.matcher(plainText(p)).find()) {
                    return false;
                }
            }
            return true;
        }

        private ObjectNode checklist(Node listNode) {
            ObjectNode data = om.createObjectNode();
            ArrayNode items = data.putArray("items");
            for (Node li = listNode.getFirstChild(); li != null; li = li.getNext()) {
                String html = inline(li.getFirstChild());
                Matcher m = TASK_PREFIX.matcher(html);
                boolean checked = m.find() && !m.group(1).isBlank();
                ObjectNode item = items.addObject();
                item.put("text", TASK_PREFIX.matcher(html).replaceFirst(""));
                item.put("checked", checked);
            }
            return block("checklist", data);
        }

        private ObjectNode code(String literal) {
            ObjectNode data = om.createObjectNode();
            data.put("code", stripTrailingNewline(literal));
            return block("code", data);
        }

        private ObjectNode image(Image img) {
            ObjectNode data = om.createObjectNode();
            data.putObject("file").put("url", img.getDestination());
            data.put("caption", AgentHtmlImageEditor.escape(plainText(img)));
            data.put("withBorder", false);
            data.put("withBackground", false);
            data.put("stretched", false);
            return block("image", data);
        }

        private ObjectNode markdownBlock(String markdown) {
            ObjectNode data = om.createObjectNode();
            data.put("markdown", markdown);
            data.put("mode", "preview");
            return block("markdown", data);
        }

        private int footnoteNumber(String label) {
            return footnoteNumbers.computeIfAbsent(label.toLowerCase(Locale.ROOT), k -> footnoteNumbers.size() + 1);
        }

        /** 脚注定义集中到文末："脚注"标题 + 有序列表；没被引用的定义排在已引用的之后 */
        private void appendFootnotes() {
            if (footnoteDefs.isEmpty()) return;
            footnoteDefs.keySet().forEach(this::footnoteNumber);
            ObjectNode title = om.createObjectNode();
            title.put("text", FOOTNOTES_TITLE);
            title.put("level", 2);
            blocks.add(block("header", title));
            ObjectNode data = om.createObjectNode();
            data.put("style", "ordered");
            data.putObject("meta").put("counterType", "numeric");
            ArrayNode items = data.putArray("items");
            for (String label : new ArrayList<>(footnoteNumbers.keySet())) {
                List<String> parts = new ArrayList<>();
                FootnoteDefinition def = footnoteDefs.get(label);
                if (def != null) {
                    for (Node c = def.getFirstChild(); c != null; c = c.getNext()) addIfNotBlank(parts, inline(c));
                }
                ObjectNode item = items.addObject();
                item.put("content", String.join("<br>", parts));
                item.putObject("meta");
                item.putArray("items");
            }
            blocks.add(block("list", data));
        }

        private ObjectNode block(String type, ObjectNode data) {
            ObjectNode block = om.createObjectNode();
            block.put("id", AgentEditorjsImageEditor.newBlockId());
            block.put("type", type);
            block.set("data", data);
            return block;
        }

        private String inline(Node parent) {
            return inlineRange(parent.getFirstChild(), null);
        }

        /** 渲染 [from, to) 区间的兄弟节点；相邻 Text 先拼起来再识别 $x$ / ==x==（commonmark 可能把一段文字拆成多个 Text） */
        private String inlineRange(Node from, Node to) {
            StringBuilder sb = new StringBuilder();
            Node c = from;
            while (c != null && c != to) {
                if (c instanceof Text) {
                    StringBuilder raw = new StringBuilder();
                    while (c != to && c instanceof Text t) {
                        raw.append(t.getLiteral());
                        c = c.getNext();
                    }
                    sb.append(renderText(raw.toString()));
                    continue;
                }
                if (c instanceof StrongEmphasis) sb.append("<b>").append(inline(c)).append("</b>");
                else if (c instanceof Emphasis) sb.append("<i>").append(inline(c)).append("</i>");
                else if (c instanceof Strikethrough) sb.append("<s>").append(inline(c)).append("</s>");
                else if (c instanceof Code code) sb.append("<code class=\"inline-code\">")
                        .append(AgentHtmlImageEditor.escape(code.getLiteral())).append("</code>");
                else if (c instanceof Link link) {
                    if (isSafeHref(link.getDestination())) {
                        sb.append("<a href=\"").append(AgentHtmlImageEditor.escape(link.getDestination()))
                                .append("\">").append(inline(c)).append("</a>");
                    } else {
                        sb.append(inline(c));
                    }
                }
                else if (c instanceof Image img) sb.append(AgentHtmlImageEditor.escape(plainText(img)));
                else if (c instanceof FootnoteReference ref) {
                    sb.append("<sup>").append(footnoteNumber(ref.getLabel())).append("</sup>");
                }
                else if (c instanceof SoftLineBreak) sb.append(' ');
                else if (c instanceof HardLineBreak) sb.append("<br>");
                else if (c instanceof HtmlInline html) sb.append(AgentHtmlImageEditor.escape(html.getLiteral()));
                else sb.append(inline(c));
                c = c.getNext();
            }
            return sb.toString();
        }
    }

    private static String stripTrailingNewline(String literal) {
        return literal.endsWith("\n") ? literal.substring(0, literal.length() - 1) : literal;
    }
}
