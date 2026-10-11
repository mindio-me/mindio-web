/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafeImageDownloaderTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0};
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    /** 测试里把 public.test 解析成公网 IP 通过校验，真正连接时改连本机 HttpServer 模拟"公网"站点 */
    private SafeImageDownloader downloaderTreatingLoopbackAsPublic() {
        return new SafeImageDownloader(host -> host.equals("public.test")
                ? new InetAddress[]{InetAddress.getByAddress("public.test", new byte[]{93, (byte) 184, (byte) 216, 34})}
                : InetAddress.getAllByName(host)) {
            @Override
            java.net.URI connectUri(java.net.URI validated) {
                return java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + validated.getRawPath());
            }
        };
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1/a.png", "http://localhost/a.png", "http://10.0.0.5/a.png",
            "http://192.168.1.2/a.png", "http://172.16.0.1/a.png", "http://169.254.169.254/latest/meta-data",
            "http://[::1]/a.png", "http://0.0.0.0/a.png", "http://100.64.0.1/a.png", "http://[fd00::1]/a.png"})
    void validate_rejectsInternalAddresses(String url) {
        assertThatThrownBy(() -> new SafeImageDownloader().validate(url))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("内网");
    }

    @ParameterizedTest
    @ValueSource(strings = {"file:///etc/passwd", "ftp://example.com/a.png", "javascript:alert(1)"})
    void validate_rejectsNonHttpSchemes(String url) {
        assertThatThrownBy(() -> new SafeImageDownloader().validate(url))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("http");
    }

    @Test
    void download_returnsImageBytesAndExtension() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/a.png", ex -> {
            ex.getResponseHeaders().add("Content-Type", "image/png");
            ex.sendResponseHeaders(200, PNG.length);
            ex.getResponseBody().write(PNG);
            ex.close();
        });
        server.start();

        SafeImageDownloader.DownloadedImage img = downloaderTreatingLoopbackAsPublic().download("http://public.test/a.png");

        assertThat(img.bytes()).isEqualTo(PNG);
        assertThat(img.extension()).isEqualTo("png");
    }

    @Test
    void download_sniffsImageWhenContentTypeIsOctetStream() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/a", ex -> {
            ex.getResponseHeaders().add("Content-Type", "application/octet-stream");
            ex.sendResponseHeaders(200, PNG.length);
            ex.getResponseBody().write(PNG);
            ex.close();
        });
        server.start();

        assertThat(downloaderTreatingLoopbackAsPublic().download("http://public.test/a").extension()).isEqualTo("png");
    }

    @Test
    void download_rejectsRedirectToInternalAddress() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/r", ex -> {
            ex.getResponseHeaders().add("Location", "http://169.254.169.254/latest/meta-data");
            ex.sendResponseHeaders(302, -1);
            ex.close();
        });
        server.start();

        assertThatThrownBy(() -> downloaderTreatingLoopbackAsPublic().download("http://public.test/r"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("内网");
    }

    @Test
    void download_rejectsNonImage() throws Exception {
        byte[] html = "<html></html>".getBytes();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/p", ex -> {
            ex.getResponseHeaders().add("Content-Type", "text/html");
            ex.sendResponseHeaders(200, html.length);
            ex.getResponseBody().write(html);
            ex.close();
        });
        server.start();

        assertThatThrownBy(() -> downloaderTreatingLoopbackAsPublic().download("http://public.test/p"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("不是图片");
    }
}
