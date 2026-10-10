/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

/** Python agents 服务连不上（没启动 / 正在重启 / 503）：调用方应稍后重试，而不是判为失败。 */
public class AgentUnavailableException extends RuntimeException {
    public AgentUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
