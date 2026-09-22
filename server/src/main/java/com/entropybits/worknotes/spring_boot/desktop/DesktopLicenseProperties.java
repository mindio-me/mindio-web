/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.desktop;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "worknotes.desktop.license")
public class DesktopLicenseProperties {

    /**
     * Only enabled for the packaged desktop runtime. Web/server deployments keep
     * their existing behavior.
     */
    private boolean enforcementEnabled = false;

    /**
     * Path to Electron's license-state.json.
     */
    private String statePath;

    /**
     * Trust anchor for licenseLease signature verification, baked into the app's
     * own config rather than read from the (attacker-writable) license-state.json.
     */
    private List<PinnedKey> pinnedPublicKeys = new ArrayList<>();

    @Data
    public static class PinnedKey {
        private String kid;
        private String algorithm;
        private String publicKeyX509Base64;
    }
}
