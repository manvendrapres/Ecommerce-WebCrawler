package com.example.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Service
public class CrawlerService {

    private static final String[] PRODUCT_URL_PATTERNS = {"/product/", "/item/", "/p/", "/products/"};

    public CompletableFuture<Set<String>> crawl(String domain) {
        Set<String> productUrls = new HashSet<>();
        try {
            Document doc = Jsoup.connect(domain).get();
            Elements links = doc.select("a[href]");

            for (Element link : links) {
                String url = link.attr("abs:href");
                if (isProductUrl(url)) {
                    productUrls.add(url);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return CompletableFuture.completedFuture(productUrls);
    }

    private boolean isProductUrl(String url) {
        for (String pattern : PRODUCT_URL_PATTERNS) {
            if (url.contains(pattern)) {
                return true;
            }
        }
        return false;
    }
}
