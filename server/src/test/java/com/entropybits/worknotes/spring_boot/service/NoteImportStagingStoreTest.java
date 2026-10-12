/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoteImportStagingStoreTest {

    @TempDir Path tmp;

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-12T00:00:00Z");
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    MindioPackageReader.ReadPackage emptyPkg() {
        return new MindioPackageReader.ReadPackage(
                new MindioPackage.Manifest(MindioPackage.FORMAT, 1, null, "web", 0, List.of()), List.of(), Map.of());
    }

    @Test
    void takeIsSingleUseAndOwnerBound() throws Exception {
        NoteImportStagingStore store = new NoteImportStagingStore(new MutableClock());
        Path dir = Files.createDirectories(tmp.resolve("s1"));
        NoteImportStagingStore.Staged s = store.stage(1L, dir, emptyPkg());

        assertThatThrownBy(() -> store.take(s.token(), 2L)).isInstanceOf(BadRequestException.class);
        assertThat(store.take(s.token(), 1L).dir()).isEqualTo(dir);
        assertThatThrownBy(() -> store.take(s.token(), 1L)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void stagingAgainForTheSameOwnerReleasesThePreviousOneOnly() throws Exception {
        NoteImportStagingStore store = new NoteImportStagingStore(new MutableClock());
        Path d1 = Files.createDirectories(tmp.resolve("o1a"));
        Path d2 = Files.createDirectories(tmp.resolve("o1b"));
        Path other = Files.createDirectories(tmp.resolve("o2"));
        NoteImportStagingStore.Staged first = store.stage(1L, d1, emptyPkg());
        NoteImportStagingStore.Staged otherOwner = store.stage(2L, other, emptyPkg());

        NoteImportStagingStore.Staged second = store.stage(1L, d2, emptyPkg());

        assertThat(d1).doesNotExist();
        assertThatThrownBy(() -> store.take(first.token(), 1L)).isInstanceOf(BadRequestException.class);
        assertThat(store.take(second.token(), 1L).dir()).isEqualTo(d2);
        assertThat(other).exists();
        assertThat(store.take(otherOwner.token(), 2L).dir()).isEqualTo(other);
    }

    @Test
    void expiredEntriesAreRejectedAndPurgedWithTheirFiles() throws Exception {
        MutableClock clock = new MutableClock();
        NoteImportStagingStore store = new NoteImportStagingStore(clock);
        Path dir = Files.createDirectories(tmp.resolve("s2"));
        Files.writeString(dir.resolve("f"), "x");
        NoteImportStagingStore.Staged s = store.stage(1L, dir, emptyPkg());

        clock.now = clock.now.plus(Duration.ofMinutes(31));
        store.purgeExpired();

        assertThat(dir).doesNotExist();
        assertThatThrownBy(() -> store.take(s.token(), 1L)).isInstanceOf(BadRequestException.class);
    }
}
