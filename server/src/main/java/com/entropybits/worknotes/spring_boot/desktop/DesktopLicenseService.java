/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class DesktopLicenseService {

    private final DesktopLicenseProperties properties;
    private final ObjectMapper objectMapper;

    public DesktopLicenseStatus getStatus() {
        Instant now = Instant.now();

        if (!properties.isEnforcementEnabled()) {
            return DesktopLicenseStatus.builder()
                    .enforcementEnabled(false)
                    .activated(true)
                    .readOnly(false)
                    .reason("ENFORCEMENT_DISABLED")
                    .checkedAt(now)
                    .build();
        }

        if (!StringUtils.hasText(properties.getStatePath())) {
            return readOnlyStatus("LICENSE_STATE_NOT_CONFIGURED", now, null, null);
        }

        File stateFile = new File(properties.getStatePath());
        if (!stateFile.isFile()) {
            return readOnlyStatus("LICENSE_STATE_MISSING", now, null, null);
        }

        try {
            JsonNode root = objectMapper.readTree(stateFile);
            String licenseLease = root.path("licenseLease").asText(null);
            String refreshToken = root.path("refreshToken").asText(null);
            String deviceId = root.path("deviceId").asText(null);

            boolean activated = StringUtils.hasText(licenseLease) && StringUtils.hasText(refreshToken);
            if (!activated) {
                return readOnlyStatus("LICENSE_NOT_ACTIVATED", now, deviceId, null);
            }

            JsonNode payload = verifyAndReadPayload(licenseLease);
            if (!"mindio-desktop".equals(payload.path("aud").asText())) {
                return readOnlyStatus("LICENSE_AUDIENCE_INVALID", now, deviceId, null);
            }
            if (payload.path("licenseVersion").asInt(0) != 1) {
                return readOnlyStatus("LICENSE_VERSION_UNSUPPORTED", now, deviceId, null);
            }
            String payloadDeviceId = payload.path("deviceId").asText(null);
            if (!StringUtils.hasText(payloadDeviceId) || !payloadDeviceId.equals(deviceId)) {
                return readOnlyStatus("LICENSE_DEVICE_MISMATCH", now, deviceId, null);
            }

            Instant expiresAt = parseInstant(payload.path("expiresAt").asText(null));
            Instant editableUntil = parseInstant(payload.path("editableUntil").asText(null));
            if (editableUntil == null) {
                return readOnlyStatus("LICENSE_EDITABLE_UNTIL_MISSING", now, deviceId, null);
            }
            if (expiresAt == null) {
                return readOnlyStatus("LICENSE_EXPIRES_AT_MISSING", now, deviceId, editableUntil);
            }

            boolean readOnly = !editableUntil.isAfter(now) || !expiresAt.isAfter(now);
            return DesktopLicenseStatus.builder()
                    .enforcementEnabled(true)
                    .activated(true)
                    .readOnly(readOnly)
                    .reason(readOnly ? "LICENSE_EXPIRED" : "ACTIVE")
                    .deviceId(deviceId)
                    .editableUntil(editableUntil)
                    .checkedAt(now)
                    .build();
        } catch (Exception ex) {
            return readOnlyStatus("LICENSE_STATE_INVALID", now, null, null);
        }
    }

    public boolean isReadOnly() {
        return getStatus().isReadOnly();
    }

    private DesktopLicenseStatus readOnlyStatus(String reason, Instant now, String deviceId, Instant editableUntil) {
        return DesktopLicenseStatus.builder()
                .enforcementEnabled(true)
                .activated(false)
                .readOnly(true)
                .reason(reason)
                .deviceId(deviceId)
                .editableUntil(editableUntil)
                .checkedAt(now)
                .build();
    }

    private Instant parseInstant(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private JsonNode verifyAndReadPayload(String licenseLease) throws Exception {
        String[] parts = licenseLease.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid JWS compact serialization");
        }

        JsonNode header = objectMapper.readTree(base64UrlDecode(parts[0]));
        if (!"EdDSA".equals(header.path("alg").asText())) {
            throw new IllegalArgumentException("Unsupported license algorithm");
        }

        String kid = header.path("kid").asText(null);
        if (!StringUtils.hasText(kid)) {
            throw new IllegalArgumentException("Missing license key id");
        }

        PublicKey publicKey = findPinnedPublicKey(kid);
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(publicKey);
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        if (!verifier.verify(base64UrlDecode(parts[2]))) {
            throw new IllegalArgumentException("License signature verification failed");
        }

        return objectMapper.readTree(base64UrlDecode(parts[1]));
    }

    private PublicKey findPinnedPublicKey(String kid) throws Exception {
        for (DesktopLicenseProperties.PinnedKey key : properties.getPinnedPublicKeys()) {
            if (kid.equals(key.getKid()) && "Ed25519".equalsIgnoreCase(key.getAlgorithm())) {
                byte[] publicKeyBytes = Base64.getDecoder().decode(key.getPublicKeyX509Base64());
                KeyFactory factory = KeyFactory.getInstance("Ed25519");
                return factory.generatePublic(new X509EncodedKeySpec(publicKeyBytes));
            }
        }

        throw new IllegalArgumentException("No pinned public key for kid=" + kid);
    }

    private byte[] base64UrlDecode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }
}
