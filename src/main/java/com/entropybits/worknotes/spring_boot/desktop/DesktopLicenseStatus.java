/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.desktop;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class DesktopLicenseStatus {

    private boolean enforcementEnabled;
    private boolean activated;
    private boolean readOnly;
    private String reason;
    private String deviceId;
    private Instant editableUntil;
    private Instant checkedAt;
}
