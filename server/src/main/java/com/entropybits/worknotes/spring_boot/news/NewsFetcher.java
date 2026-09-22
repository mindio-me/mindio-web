/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.news;

import java.util.List;

public interface NewsFetcher {
    String getSourceKey();
    List<NewsItemData> fetch() throws Exception;
}
