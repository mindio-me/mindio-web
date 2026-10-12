/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * .mindio 包导出/导入时处理笔记正文里的资源地址：把指向本实例上传目录的 URL 找出来并改写成
 * asset://&lt;sha256&gt;.&lt;ext&gt;，导入时再还原成本机上传地址。按"可达性"判断：相对路径
 * /api/uploads/...、localhost/127.0.0.1、当前请求的 origin、配置的 upload url-prefix 算本机；
 * 别的服务器上同样路径的地址是外链，原样保留。
 */
public class NoteAssetRewriter {

    public static final String ASSET_SCHEME = "asset://";

    public record UploadRef(String url, Path file) {}

    private static final String DEFAULT_PREFIX = "/api";
    private static final String LOOPBACK_ORIGIN = "https?://(?:localhost|127\\.0\\.0\\.1)(?::\\d+)?";
    /** 相对路径前面不能紧挨着主机名字符，否则 https://other.com/api/uploads/... 会被截成相对路径 */
    private static final String NOT_AFTER_HOST = "(?<![\\w.:/-])";
    private static final String SUFFIX = "([^\"'\\s)<>\\\\]+)";
    private static final Pattern ASSET_REF = Pattern.compile("asset://([0-9a-f]{64}(?:\\.[a-z0-9]{1,10})?)");
    private static final Pattern EXT = Pattern.compile("[a-z0-9]{1,10}");

    private final Pattern uploadUrl;
    private final Path uploadRoot;

    public NoteAssetRewriter(String uploadUrlPrefix, Path uploadRoot, Collection<String> extraOrigins) {
        this.uploadRoot = uploadRoot.toAbsolutePath().normalize();
        StringBuilder origins = new StringBuilder(LOOPBACK_ORIGIN);
        for (String origin : extraOrigins) {
            if (origin != null && !origin.isBlank()) {
                origins.append('|').append(Pattern.quote(stripTrailingSlash(origin.trim())));
            }
        }
        List<String> alternatives = new ArrayList<>();
        alternatives.add(relativeAlternative(DEFAULT_PREFIX, origins.toString()));
        String configured = uploadUrlPrefix == null ? "" : stripTrailingSlash(uploadUrlPrefix.trim());
        if (!configured.isEmpty() && !configured.equals(DEFAULT_PREFIX)) {
            alternatives.add(configured.matches("(?i)https?://.*")
                    ? Pattern.quote(configured) + "/uploads/"
                    : relativeAlternative(configured, origins.toString()));
        }
        this.uploadUrl = Pattern.compile("(?:" + String.join("|", alternatives) + ")" + SUFFIX);
    }

    private static String relativeAlternative(String prefix, String origins) {
        return "(?:(?:" + origins + ")|" + NOT_AFTER_HOST + ")" + Pattern.quote(prefix) + "/uploads/";
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    public List<UploadRef> findUploads(String text) {
        List<UploadRef> refs = new ArrayList<>();
        if (text == null || text.isEmpty()) return refs;
        Matcher m = uploadUrl.matcher(text);
        while (m.find()) {
            refs.add(new UploadRef(m.group(), resolve(m.group(1))));
        }
        return refs;
    }

    private Path resolve(String suffix) {
        String path = suffix.split("[?#]", 2)[0];
        Path file = uploadRoot.resolve(path).normalize();
        return file.startsWith(uploadRoot) ? file : null;
    }

    public String replaceUploads(String text, Map<String, String> urlToReplacement) {
        if (text == null || text.isEmpty() || urlToReplacement.isEmpty()) return text;
        Matcher m = uploadUrl.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(urlToReplacement.getOrDefault(m.group(), m.group())));
        }
        m.appendTail(out);
        return out.toString();
    }

    public Set<String> findAssetNames(String text) {
        Set<String> names = new LinkedHashSet<>();
        if (text == null || text.isEmpty()) return names;
        Matcher m = ASSET_REF.matcher(text);
        while (m.find()) names.add(m.group(1));
        return names;
    }

    public String restoreAssets(String text, Map<String, String> assetNameToUrl) {
        if (text == null || text.isEmpty() || assetNameToUrl.isEmpty()) return text;
        Matcher m = ASSET_REF.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String url = assetNameToUrl.get(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(url != null ? url : m.group()));
        }
        m.appendTail(out);
        return out.toString();
    }

    public static String assetName(String sha256Hex, Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return EXT.matcher(ext).matches() ? sha256Hex + "." + ext : sha256Hex;
    }

    public static String sha256Hex(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) digest.update(buf, 0, n);
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
