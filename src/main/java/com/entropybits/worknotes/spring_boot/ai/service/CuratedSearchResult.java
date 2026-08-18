/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.ai.service;

import com.entropybits.worknotes.spring_boot.search.SearchResultItem;

import java.util.List;

public record CuratedSearchResult(String reply, List<SearchResultItem> results) {}
