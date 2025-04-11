package com.example.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;

@Service
public class CrawlerService {

    private static final Logger logger = LoggerFactory.getLogger(CrawlerService.class);

    private static final String[] PRODUCT_URL_PATTERNS = {"/product/", "/item/", "/p/", "/products/"};
    private static final int MAX_DEPTH = 2; // Limit the depth of crawling
    private static final int MAX_THREADS = 10;
    private static final long REQUEST_DELAY_MS = 2000;
    private static final long RETRY_DELAY_MS = 5000;

    private final ExecutorService executorService = Executors.newFixedThreadPool(MAX_THREADS);
    private final Set<String> visitedUrls = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Set<String>> robotsCache = new ConcurrentHashMap<>(); // Cache for disallowed paths


    public CompletableFuture<Set<String>> crawl(String domain) {
        Set<String> productUrls = new HashSet<>();
        String targetDomain = getDomain(domain);
        return crawl(domain, productUrls, 0, targetDomain);
    }

    private CompletableFuture<Set<String>> crawl(String url, Set<String> productUrls, int depth, String targetDomain) {
        String currentDomain = getDomain(url);

        if (depth > MAX_DEPTH || visitedUrls.contains(url) || currentDomain == null) {
            return CompletableFuture.completedFuture(productUrls);
        }

        visitedUrls.add(url);

        // Respect robots.txt
        if (!isCrawlAllowed(url)) {
            return CompletableFuture.completedFuture(productUrls);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Introduce a delay to respect rate limiting
                Thread.sleep(REQUEST_DELAY_MS);

                Document doc = Jsoup.connect(url).get();
                Elements links = doc.select("a[href]");

                for (Element link : links) {
                    String absUrl = link.attr("abs:href");
                    logger.debug("absUrl : {}", absUrl);

                    if( isValidUrl(absUrl) && targetDomain.equalsIgnoreCase(normalizeDomain(getDomain(absUrl))) ){
                        if (isProductUrl(absUrl)) {
                            productUrls.add(absUrl);
                        } else {
                            // Recursively crawl the link if it's not a product URL
                            crawl(absUrl, productUrls, depth + 1, targetDomain);
                        }
                    }
                }
            } catch (IOException e) {
                handleIOException(url, e, targetDomain);
//                logger.debug("Error fetching URL: {} - {}", url, e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore interrupted status
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

    private boolean isValidUrl(String url) {
        return url.startsWith("http://") || url.startsWith("https://");
    }

    private String getDomain(String url) {
        try {
            URL parsedUrl = new URL(url);
            return parsedUrl.getHost();
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeDomain(String domain) {
        if (domain == null) return null;
        return domain.startsWith("www.") ? domain.substring(4) : domain;
    }

    private boolean isCrawlAllowed(String url) {
        try {
            String domain = new URL(url).getHost();
            Set<String> disallowed = robotsCache.computeIfAbsent(domain, this::fetchAndParseRobots);
            return disallowed.stream().noneMatch(url::contains);
        } catch (Exception e) {
            logger.debug("Error fetching URL while checking for crawling permission : {} - {}", url, e.getMessage());
            return true;
        }
    }

    private Set<String> fetchAndParseRobots(String domain) {
        Set<String> disallowedPaths = new HashSet<>();
        try {
            String robotsUrl = "https://" + domain + "/robots.txt";
            Document robotsDoc = Jsoup.connect(robotsUrl).get();
            String[] lines = robotsDoc.body().text().split("\n");

            for (String line : lines) {
                if (line.startsWith("Disallow:")) {
                    String disallowedPath = line.replace("Disallow:", "").trim();
                    if (!disallowedPath.isEmpty()) {
                        disallowedPaths.add(disallowedPath);
                    }
                }
            }

        } catch (IOException e) {
            logger.warn("Error fetching robots.txt : {}", e.getMessage());
        }
        return disallowedPaths;
    }


    private void handleIOException(String url, IOException e, String targetDomain) {
        if (e.getMessage().contains("429")) {
            logger.warn("To many requests Error fetching URL: {} - Status=429", url);
            try {
                Thread.sleep(RETRY_DELAY_MS);
                crawl(url, new HashSet<>(), 0, targetDomain); // Retry crawling the same URL
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt(); // Restore interrupted status
            }
        } else if (e.getMessage().contains("500")) {
            logger.warn("Server Error fetching URL: {} - Status=500", url);
        } else {
            logger.error("Error fetching URL: {} - {}", url, e.getMessage());
        }
    }
}