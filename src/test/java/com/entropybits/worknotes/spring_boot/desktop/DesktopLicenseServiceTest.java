/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.desktop;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DesktopLicenseServiceTest {

    private static final String KID = "mindio-test-key";

    @TempDir
    File tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsLeaseSignedByThePinnedKey() throws Exception {
        KeyPair realKeyPair = generateKeyPair();
        File stateFile = writeState(realKeyPair, KID, Instant.now().plusSeconds(3600));
        DesktopLicenseService service = new DesktopLicenseService(properties(stateFile, realKeyPair), objectMapper);

        DesktopLicenseStatus status = service.getStatus();
        assertThat(status.isActivated()).isTrue();
        assertThat(status.isReadOnly()).isFalse();
        assertThat(status.getReason()).isEqualTo("ACTIVE");
    }

    @Test
    void rejectsLeaseSignedByAKeyThatIsNotPinned_evenWhenLicenseStateClaimsToTrustIt() throws Exception {
        KeyPair pinnedKeyPair = generateKeyPair();
        KeyPair attackerKeyPair = generateKeyPair();

        // The lease AND the attacker's own "licensePublicKeys" both live in the same
        // attacker-writable state file. Before the pinning fix, verification trusted
        // whatever key was embedded there, so this forged lease would have verified
        // successfully. With pinning, only attackerKeyPair's signature is self-consistent
        // with the state file, but the server config only trusts pinnedKeyPair.
        File stateFile = writeState(attackerKeyPair, KID, Instant.now().plusSeconds(3600));
        DesktopLicenseService service = new DesktopLicenseService(properties(stateFile, pinnedKeyPair), objectMapper);

        DesktopLicenseStatus status = service.getStatus();
        assertThat(status.isActivated()).isFalse();
        assertThat(status.isReadOnly()).isTrue();
        assertThat(status.getReason()).isEqualTo("LICENSE_STATE_INVALID");
    }

    @Test
    void rejectsLeaseWhoseKidHasNoPinnedMatch() throws Exception {
        KeyPair keyPair = generateKeyPair();
        File stateFile = writeState(keyPair, "some-other-kid", Instant.now().plusSeconds(3600));
        DesktopLicenseService service = new DesktopLicenseService(properties(stateFile, keyPair), objectMapper);

        DesktopLicenseStatus status = service.getStatus();
        assertThat(status.isActivated()).isFalse();
        assertThat(status.isReadOnly()).isTrue();
        assertThat(status.getReason()).isEqualTo("LICENSE_STATE_INVALID");
    }

    @Test
    void isReadOnlyWhenEditableUntilHasPassed() throws Exception {
        KeyPair keyPair = generateKeyPair();
        File stateFile = writeState(keyPair, KID, Instant.now().minusSeconds(60));
        DesktopLicenseService service = new DesktopLicenseService(properties(stateFile, keyPair), objectMapper);

        DesktopLicenseStatus status = service.getStatus();
        assertThat(status.isActivated()).isTrue();
        assertThat(status.isReadOnly()).isTrue();
        assertThat(status.getReason()).isEqualTo("LICENSE_EXPIRED");
    }

    private DesktopLicenseProperties properties(File stateFile, KeyPair pinnedKeyPair) {
        DesktopLicenseProperties properties = new DesktopLicenseProperties();
        properties.setEnforcementEnabled(true);
        properties.setStatePath(stateFile.getAbsolutePath());

        DesktopLicenseProperties.PinnedKey pinnedKey = new DesktopLicenseProperties.PinnedKey();
        pinnedKey.setKid(KID);
        pinnedKey.setAlgorithm("Ed25519");
        pinnedKey.setPublicKeyX509Base64(Base64.getEncoder().encodeToString(pinnedKeyPair.getPublic().getEncoded()));
        properties.setPinnedPublicKeys(java.util.List.of(pinnedKey));
        return properties;
    }

    private File writeState(KeyPair signingKeyPair, String kid, Instant editableUntil) throws Exception {
        String deviceId = "device-1";
        String lease = sign(signingKeyPair, kid, deviceId, editableUntil);

        Map<String, Object> state = Map.of(
                "deviceId", deviceId,
                "refreshToken", "irrelevant-refresh-token",
                "licenseLease", lease,
                "licensePublicKeys", java.util.List.of(Map.of(
                        "kid", kid,
                        "algorithm", "Ed25519",
                        "publicKey", Base64.getEncoder().encodeToString(signingKeyPair.getPublic().getEncoded()))));

        File stateFile = new File(tempDir, "license-state-" + System.nanoTime() + ".json");
        Files.writeString(stateFile.toPath(), objectMapper.writeValueAsString(state));
        return stateFile;
    }

    private String sign(KeyPair keyPair, String kid, String deviceId, Instant editableUntil) throws Exception {
        Map<String, Object> header = Map.of("alg", "EdDSA", "typ", "JWT", "kid", kid);
        Map<String, Object> payload = Map.of(
                "aud", "mindio-desktop",
                "deviceId", deviceId,
                "licenseVersion", 1,
                "editableUntil", editableUntil.toString(),
                "expiresAt", editableUntil.toString());

        String encodedHeader = base64Url(objectMapper.writeValueAsBytes(header));
        String encodedPayload = base64Url(objectMapper.writeValueAsBytes(payload));
        String signingInput = encodedHeader + "." + encodedPayload;

        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(keyPair.getPrivate());
        signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return signingInput + "." + base64Url(signature.sign());
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private KeyPair generateKeyPair() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }
}
