/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateConversationTitleRequestTest {

    private final Validator validator;

    UpdateConversationTitleRequestTest() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void blankTitleFailsValidation() {
        UpdateConversationTitleRequest request = new UpdateConversationTitleRequest();
        request.setTitle("   ");

        Set<ConstraintViolation<UpdateConversationTitleRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
    }

    @Test
    void nonBlankTitlePassesValidation() {
        UpdateConversationTitleRequest request = new UpdateConversationTitleRequest();
        request.setTitle("新标题");

        Set<ConstraintViolation<UpdateConversationTitleRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }
}
