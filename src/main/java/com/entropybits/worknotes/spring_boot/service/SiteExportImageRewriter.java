/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 站点导出时使用：把内容里引用本地上传目录的图片地址，
 * 改写为不带前导斜杠的站内相对路径 assets/uploads/...（导出站点要支持
 * file:// 直接双击打开和子目录托管，路径必须相对于 index.html 才能解析
 * 正确；带前导斜杠会被浏览器当成相对于文件系统/域名根目录处理），并记录
 * 需要拷贝的源文件。
 */
public class SiteExportImageRewriter {

    public record Match(String originalUrl, String relativeAssetPath, Path sourceFile) {}

    public record RewriteResult(String text, List<Match> matches) {}

    private final Pattern uploadUrlPattern;
    private final Path uploadRoot;

    public SiteExportImageRewriter(String uploadUrlPrefix, Path uploadRoot) {
        String prefix = uploadUrlPrefix == null ? "" : uploadUrlPrefix;
        this.uploadUrlPattern = Pattern.compile(Pattern.quote(prefix) + "/uploads/([^\"'\\s)>]+)");
        this.uploadRoot = uploadRoot;
    }

    public RewriteResult rewriteText(String text) {
        if (text == null || text.isBlank()) {
            return new RewriteResult(text, List.of());
        }

        List<Match> matches = new ArrayList<>();
        StringBuilder rewritten = new StringBuilder();
        Matcher matcher = uploadUrlPattern.matcher(text);
        int lastEnd = 0;

        while (matcher.find()) {
            String suffix = matcher.group(1);
            String originalUrl = matcher.group();
            String relativeAssetPath = "assets/uploads/" + suffix;
            Path sourceFile = uploadRoot.resolve(suffix).normalize();

            rewritten.append(text, lastEnd, matcher.start()).append(relativeAssetPath);
            lastEnd = matcher.end();
            matches.add(new Match(originalUrl, relativeAssetPath, sourceFile));
        }
        rewritten.append(text.substring(lastEnd));

        return new RewriteResult(rewritten.toString(), matches);
    }

    public Optional<Match> matchSingleUrl(String url) {
        if (url == null || url.isBlank()) return Optional.empty();
        RewriteResult result = rewriteText(url);
        return result.matches().isEmpty() ? Optional.empty() : Optional.of(result.matches().get(0));
    }
}
