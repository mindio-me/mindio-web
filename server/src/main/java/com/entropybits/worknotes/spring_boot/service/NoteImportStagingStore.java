/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 导入"预检 → 用户确认 → 执行"之间暂存已解析的包，执行时不必再上传一次。只放内存：
 * 单实例部署，重启后用户重新选文件即可。token 一次性、绑定用户、30 分钟过期。
 */
@Component
public class NoteImportStagingStore {

    public record Staged(String token, Long ownerId, Path dir, MindioPackageReader.ReadPackage pkg, Instant expiresAt) {}

    private static final Duration TTL = Duration.ofMinutes(30);
    private static final String EXPIRED = "导入已过期，请重新选择备份文件";

    private final Map<String, Staged> byToken = new ConcurrentHashMap<>();
    private final Clock clock;

    public NoteImportStagingStore() {
        this(Clock.systemUTC());
    }

    public NoteImportStagingStore(Clock clock) {
        this.clock = clock;
    }

    /** 每个用户同时只保留一份暂存：再次预检会释放该用户之前未提交的暂存，避免临时目录无上限累积 */
    public Staged stage(Long ownerId, Path dir, MindioPackageReader.ReadPackage pkg) {
        byToken.values().removeIf(old -> {
            if (!old.ownerId().equals(ownerId)) return false;
            release(old);
            return true;
        });
        Staged s = new Staged(UUID.randomUUID().toString(), ownerId, dir, pkg, clock.instant().plus(TTL));
        byToken.put(s.token(), s);
        return s;
    }

    public Staged take(String token, Long ownerId) {
        Staged s = token == null ? null : byToken.get(token);
        if (s == null || !s.ownerId().equals(ownerId)) throw new BadRequestException(EXPIRED);
        if (!byToken.remove(token, s)) throw new BadRequestException(EXPIRED);
        if (clock.instant().isAfter(s.expiresAt())) {
            release(s);
            throw new BadRequestException(EXPIRED);
        }
        return s;
    }

    public void release(Staged s) {
        try {
            FileSystemUtils.deleteRecursively(s.dir());
        } catch (IOException ignored) {
            // 临时目录残留不影响功能，下次重启后由系统临时目录清理
        }
    }

    @Scheduled(fixedDelay = 10 * 60 * 1000)
    public void purgeExpired() {
        Instant now = clock.instant();
        byToken.values().removeIf(s -> {
            if (now.isAfter(s.expiresAt())) {
                release(s);
                return true;
            }
            return false;
        });
    }
}
