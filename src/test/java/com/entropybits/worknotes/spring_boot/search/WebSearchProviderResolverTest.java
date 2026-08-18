/*
 * Copyright (c) 2026 Fasong Wu
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.entropybits.worknotes.spring_boot.search;

import com.entropybits.worknotes.spring_boot.search.config.SearchProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class WebSearchProviderResolverTest {

    private final RssNewsSearchProvider rssProvider = mock(RssNewsSearchProvider.class);
    private final TavilySearchProvider tavilyProvider = mock(TavilySearchProvider.class);

    @Test
    void resolve_returnsRssProviderWhenNoTavilyKeyConfigured() {
        SearchProperties props = new SearchProperties();
        WebSearchProviderResolver resolver = new WebSearchProviderResolver(props, rssProvider, tavilyProvider);

        assertThat(resolver.resolve()).isSameAs(rssProvider);
    }

    @Test
    void resolve_returnsTavilyProviderWhenKeyConfigured() {
        SearchProperties props = new SearchProperties();
        props.getTavily().setApiKey("real-key");
        WebSearchProviderResolver resolver = new WebSearchProviderResolver(props, rssProvider, tavilyProvider);

        assertThat(resolver.resolve()).isSameAs(tavilyProvider);
    }
}
