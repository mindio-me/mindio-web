/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.search;

import java.util.List;

public interface WebSearchProvider {
    List<SearchResultItem> search(String query, int limit) throws Exception;
}
