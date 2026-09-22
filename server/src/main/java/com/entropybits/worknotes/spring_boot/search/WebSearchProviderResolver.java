/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.search;

import com.entropybits.worknotes.spring_boot.search.config.SearchProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class WebSearchProviderResolver {

    private final SearchProperties searchProperties;
    private final RssNewsSearchProvider rssProvider;
    private final TavilySearchProvider tavilyProvider;

    public WebSearchProvider resolve() {
        return StringUtils.hasText(searchProperties.getTavily().getApiKey()) ? tavilyProvider : rssProvider;
    }
}
