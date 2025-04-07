package com.example.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;

@Service
public class CrawlerService {

    private static final String[] PRODUCT_URL_PATTERNS = {"/product/", "/item/", "/p/", "/products/"};
    private static final int MAX_DEPTH = 3; // Limit the depth of crawling
    private static final int MAX_THREADS = 10; // Limit the number of concurrent threads

    private final ExecutorService executorService = Executors.newFixedThreadPool(MAX_THREADS);
    private final Set<String> visitedUrls = ConcurrentHashMap.newKeySet();

    public CompletableFuture<Set<String>> crawl(String domain) {
        Set<String> productUrls = new HashSet<>();
        return crawl(domain, productUrls, 0);
    }

    private CompletableFuture<Set<String>> crawl(String url, Set<String> productUrls, int depth) {
        if (depth > MAX_DEPTH || visitedUrls.contains(url)) {
            return CompletableFuture.completedFuture(productUrls);
        }

        visitedUrls.add(url);

        return CompletableFuture.supplyAsync(() -> {
            try {
                Document doc = Jsoup.connect(url).get();
                Elements links = doc.select("a[href]");

                for (Element link : links) {
                    String absUrl = link.attr("abs:href");
                    if (isProductUrl(absUrl)) {
                        productUrls.add(absUrl);
                    } else {
                        // Recursively crawl the link if it's not a product URL
                        crawl(absUrl, productUrls, depth + 1);
                    }
                }
            } catch (IOException e) {
                // Log the error and continue
                System.err.println("Error fetching URL: " + url + " - " + e.getMessage());
            }
            return productUrls;
        }, executorService).thenCompose(result -> CompletableFuture.completedFuture(result));
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