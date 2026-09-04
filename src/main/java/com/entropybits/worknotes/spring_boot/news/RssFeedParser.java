/*
 * Copyright (c) 2026 Fasong Wu
 * Licensed under the MindIO Pro Source License. See LICENSE-PRO for terms.
 */

package com.entropybits.worknotes.spring_boot.news;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Component
public class RssFeedParser {

    public List<NewsItemData> parse(String rssUrl, int maxItems) throws IOException {
        List<NewsItemData> result = new ArrayList<>();
        HttpURLConnection conn = (HttpURLConnection) URI.create(rssUrl).toURL().openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; Notecast/1.0)");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);

        try {
            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("RSS request returned HTTP " + status + " for " + rssUrl);
            }

            try (InputStream is = conn.getInputStream()) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "");
                factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "");
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(is);

                NodeList items = doc.getElementsByTagName("item");
                int count = Math.min(items.getLength(), maxItems);
                for (int i = 0; i < count; i++) {
                    Element item = (Element) items.item(i);
                    String title = getTagText(item, "title");
                    String link = getTagText(item, "link");
                    if (title != null && !title.isBlank()) {
                        result.add(new NewsItemData(i + 1, title.trim(), link != null ? link.trim() : null));
                    }
                }
            }
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Invalid RSS response from " + rssUrl + ": " + e.getMessage(), e);
        } finally {
            conn.disconnect();
        }
        return result;
    }

    private String getTagText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent();
        }
        return null;
    }
}
