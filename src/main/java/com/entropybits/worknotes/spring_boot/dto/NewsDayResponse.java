/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.dto;

import java.time.LocalDate;
import java.util.List;

public record NewsDayResponse(
        LocalDate date,
        List<NewsFeedResponse> feeds
) {}
