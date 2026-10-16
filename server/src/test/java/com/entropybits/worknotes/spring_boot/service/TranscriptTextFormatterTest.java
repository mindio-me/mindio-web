/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

import com.entropybits.worknotes.spring_boot.entity.AudioTranscription;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TranscriptTextFormatterTest {

    private final TranscriptTextFormatter formatter = new TranscriptTextFormatter(new ObjectMapper());

    private static final String TWO_SPEAKERS = "["
            + "{\"start\":0,\"end\":1000,\"text\":\"我们定一下预算。\",\"speaker\":1},"
            + "{\"start\":1200,\"end\":2000,\"text\":\"上限五十万。\",\"speaker\":1},"
            + "{\"start\":65000,\"end\":66000,\"text\":\"好的我来排期。\",\"speaker\":2},"
            + "{\"start\":70000,\"end\":70000,\"text\":\"[第 2 段转写失败]\"}]";

    private static AudioTranscription done(String utterances) {
        return AudioTranscription.builder().noteId(1L).blockId("b").status(AudioTranscription.Status.DONE)
                .utterances(utterances).build();
    }

    @Test
    void readingUnits_groupSpeakerTurnsWithTimeAndName() {
        AudioTranscription t = done(TWO_SPEAKERS);
        t.setSpeakerNames("{\"1\":\"张总\"}");

        List<String> units = formatter.readingUnits(t);

        assertThat(units).contains("[00:00 张总] 我们定一下预算。上限五十万。", "[01:05 说话人 2] 好的我来排期。");
        assertThat(String.join("\n", units)).doesNotContain("转写失败");
    }

    @Test
    void readingUnits_singleSpeakerHasTimeButNoName() {
        AudioTranscription t = done("[{\"start\":0,\"end\":1,\"text\":\"甲。\",\"speaker\":1},"
                + "{\"start\":3725000,\"end\":3726000,\"text\":\"乙。\",\"speaker\":1}]");

        List<String> units = formatter.readingUnits(t);

        assertThat(String.join("\n", units)).contains("[00:00] 甲。乙。").doesNotContain("说话人");
    }

    @Test
    void readingUnits_splitsLongTurnWithNewTimestamp() {
        String longText = "很".repeat(200) + "。";
        AudioTranscription t = done("[{\"start\":0,\"end\":1,\"text\":\"" + longText + "\"},"
                + "{\"start\":5000,\"end\":6000,\"text\":\"" + longText + "\"}]");

        List<String> units = formatter.readingUnits(t);

        assertThat(units).anyMatch(u -> u.startsWith("[00:00] "));
        assertThat(units).anyMatch(u -> u.startsWith("[00:05] "));
    }

    @Test
    void readingUnits_includesSceneOverviewKeyPointsChaptersAndTodos() {
        AudioTranscription t = done(TWO_SPEAKERS);
        t.setScene(AudioTranscription.Scene.MEETING);
        t.setSummary("{\"overview\":\"{{S1}}定了预算\","
                + "\"keyPoints\":[{\"topic\":\"预算\",\"points\":[\"**上限**：五十万\"]}],"
                + "\"chapters\":[{\"start\":65000,\"title\":\"排期\"}],"
                + "\"todos\":[\"{{S2}}出排期表\"]}");

        String all = String.join("\n", formatter.readingUnits(t));

        assertThat(all).contains("场景：会议").contains("概述：说话人 1定了预算")
                .contains("预算：上限：五十万").contains("[01:05] 排期").contains("待办：说话人 2出排期表")
                .doesNotContain("**").doesNotContain("{{S");
    }

    @Test
    void indexUnits_prefixSpeakerNameOnChangeWithoutTimestamps() {
        AudioTranscription t = done(TWO_SPEAKERS);
        t.setSpeakerNames("{\"1\":\"张总\"}");
        t.setPolished("[{\"start\":0,\"text\":\"我们定一下预算，上限五十万。\"},{\"start\":65000,\"text\":\"好的，我来排期。\"}]");

        List<String> units = formatter.indexUnits(t);

        assertThat(units).containsExactly("张总：我们定一下预算，上限五十万。", "说话人 2：好的，我来排期。");
    }

    @Test
    void indexUnits_noSpeakerPrefixForSingleSpeakerAndKeepsOverview() {
        AudioTranscription t = done("[{\"start\":0,\"end\":1,\"text\":\"甲。\",\"speaker\":1}]");
        t.setSummary("{\"overview\":\"随手记\"}");
        t.setPolished("[{\"start\":0,\"text\":\"甲。\"}]");

        assertThat(formatter.indexUnits(t)).containsExactly("随手记", "甲。");
    }
}
