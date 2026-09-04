/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.dto;

import java.util.List;

public record NewsFeedResponse(
        NewsSourceConfigResponse source,
        List<NewsItemResponse> items
) {}
