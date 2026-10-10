/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.service;

/** Python agents 返回了业务错误（detail.code / detail.message），重试也不会变好。 */
public class AgentCallException extends RuntimeException {
    private final String code;

    public AgentCallException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
