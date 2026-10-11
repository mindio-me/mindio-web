/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * agent 插入"网上的图片"时用的下载器。图片地址可能来自网页内容（prompt 注入可以诱导模型
 * 传任意地址），所以在发请求前校验：只许 http/https，主机名解析出的每个 IP 都不能是本机/
 * 内网/链路本地（含云元数据 169.254.169.254）；不自动跟随重定向，每一跳都重新校验。
 * 已知局限：校验和真正连接之间存在 DNS 重新解析（DNS rebinding），这里不处理。
 */
@Component
public class SafeImageDownloader {

    @FunctionalInterface
    interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    public record DownloadedImage(byte[] bytes, String mimeType, String extension) {}

    private static final int MAX_REDIRECTS = 3;
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final Map<String, String> EXT_BY_MIME = Map.of(
            "image/png", "png", "image/jpeg", "jpg", "image/jpg", "jpg", "image/gif", "gif",
            "image/webp", "webp", "image/bmp", "bmp");

    private final HostResolver resolver;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public SafeImageDownloader() {
        this(InetAddress::getAllByName);
    }

    SafeImageDownloader(HostResolver resolver) {
        this.resolver = resolver;
    }

    public DownloadedImage download(String url) {
        URI current = validate(url);
        try {
            for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
                HttpRequest request = HttpRequest.newBuilder(connectUri(current))
                        .timeout(Duration.ofSeconds(20))
                        .header("User-Agent", "Mozilla/5.0 (compatible; mindio-agent)")
                        .GET().build();
                HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                int code = response.statusCode();
                if (code >= 300 && code < 400) {
                    response.body().close();
                    String location = response.headers().firstValue("location")
                            .orElseThrow(() -> new BadRequestException("图片地址重定向缺少 Location"));
                    current = validate(current.resolve(location).toString());
                    continue;
                }
                if (code != 200) {
                    response.body().close();
                    throw new BadRequestException("下载图片失败，HTTP " + code);
                }
                String mime = response.headers().firstValue("content-type").orElse("")
                        .split(";")[0].trim().toLowerCase();
                byte[] bytes = readLimited(response.body());
                String ext = EXT_BY_MIME.get(mime);
                if (ext == null) ext = sniffExtension(bytes);
                if (ext == null) {
                    throw new BadRequestException("这个地址返回的不是图片（" + (mime.isEmpty() ? "未知类型" : mime) + "）");
                }
                return new DownloadedImage(bytes, mime.startsWith("image/") ? mime : "image/" + ext, ext);
            }
        } catch (IOException e) {
            throw new BadRequestException("下载图片失败: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BadRequestException("下载图片被中断");
        }
        throw new BadRequestException("图片地址重定向次数过多");
    }

    URI validate(String url) {
        URI uri;
        try {
            uri = new URI(url == null ? "" : url.trim());
        } catch (URISyntaxException e) {
            throw new BadRequestException("图片地址格式不对");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new BadRequestException("只支持 http/https 图片地址");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BadRequestException("图片地址缺少主机名");
        }
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(host.startsWith("[") ? host.substring(1, host.length() - 1) : host);
        } catch (UnknownHostException e) {
            throw new BadRequestException("无法解析图片地址的主机名: " + host);
        }
        for (InetAddress address : addresses) {
            if (isInternal(address)) {
                throw new BadRequestException("不允许访问本机或内网地址: " + host);
            }
        }
        return uri;
    }

    /** 测试钩子：生产环境直接连校验过的地址 */
    URI connectUri(URI validated) {
        return validated;
    }

    static boolean isInternal(InetAddress a) {
        if (a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress()
                || a.isAnyLocalAddress() || a.isMulticastAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (b.length == 16) return (b[0] & 0xfe) == 0xfc;                     // IPv6 ULA fc00::/7
        int first = b[0] & 0xff;
        return first == 0 || (first == 100 && (b[1] & 0xc0) == 64);         // 0.0.0.0/8、CGNAT 100.64/10
    }

    private static byte[] readLimited(InputStream in) throws IOException {
        try (in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            long total = 0;
            int n;
            while ((n = in.read(buffer)) != -1) {
                total += n;
                if (total > MAX_BYTES) throw new BadRequestException("图片超过 10MB");
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }

    private static String sniffExtension(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xff) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "png";
        if (b.length >= 3 && (b[0] & 0xff) == 0xFF && (b[1] & 0xff) == 0xD8 && (b[2] & 0xff) == 0xFF) return "jpg";
        if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F') return "gif";
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "webp";
        return null;
    }
}
