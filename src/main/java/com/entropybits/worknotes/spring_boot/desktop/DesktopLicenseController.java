/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.entropybits.worknotes.spring_boot.desktop;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/desktop/license")
@RequiredArgsConstructor
public class DesktopLicenseController {

    private final DesktopLicenseService desktopLicenseService;

    @GetMapping("/status")
    public DesktopLicenseStatus getStatus() {
        return desktopLicenseService.getStatus();
    }
}
