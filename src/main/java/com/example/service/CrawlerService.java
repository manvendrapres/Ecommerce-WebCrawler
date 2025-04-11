package com.example.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;

@Service
public class CrawlerService {

    private static final String[] PRODUCT_URL_PATTERNS = {"/product/", "/item/", "/p/", "/products/"};
    private static final int MAX_DEPTH = 1; // Limit the depth of crawling
    private static final int MAX_THREADS = 10; // Limit the number of concurrent threads
    private static final long REQUEST_DELAY_MS = 2000; // Delay between requests in milliseconds
    private static final long RETRY_DELAY_MS = 5000; // Delay before retrying after an error

    private final ExecutorService executorService = Executors.newFixedThreadPool(MAX_THREADS);
    private final Set<String> visitedUrls = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Set<String>> robotsCache = new ConcurrentHashMap<>(); // Cache for disallowed paths


    public CompletableFuture<Set<String>> crawl(String domain) {
        Set<String> productUrls = new HashSet<>();
        return crawl(domain, productUrls, 0);
    }

    private CompletableFuture<Set<String>> crawl(String url, Set<String> productUrls, int depth) {
        if (depth > MAX_DEPTH || visitedUrls.contains(url)) {
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
//                    System.out.println("absUrl : " + absUrl );
                    if (isProductUrl(absUrl)) {
                        productUrls.add(absUrl);
                    } else if (isValidUrl(absUrl)) {
                        // Recursively crawl the link if it's not a product URL
                        crawl(absUrl, productUrls, depth + 1);
                    }
                }
            } catch (IOException e) {
                handleIOException(url, e);
//                System.err.println("Error fetching URL: " + url + " - " + e.getMessage());
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

    private boolean isCrawlAllowed(String url) {
        String domain = getDomain(url);
        Set<String> disallowedPaths = robotsCache.get(domain);

        if (disallowedPaths == null) {
            disallowedPaths = fetchAndParseRobots(domain);
            robotsCache.put(domain, disallowedPaths); // Cache the disallowed paths
        }

        // Check if the URL matches any disallowed paths
        return disallowedPaths.stream().noneMatch(url::contains);
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
                    disallowedPaths.add(disallowedPath);
                }
            }
//            for (String line : lines) {
//                if (line.startsWith("User -agent: *")) {
//                    for (String disallow : lines) {
//                        if (disallow.startsWith("Disallow:")) {
//                            String disallowedPath = disallow.replace("Disallow:", "").trim();
//                            disallowedPaths.add(disallowedPath);
//                        }
//                    }
//                }
//            }

        } catch (IOException e) {
            System.err.println("Error fetching robots.txt : " + e.getMessage());
        }
        return disallowedPaths;
    }

    private String getDomain(String url) {
        try {
            URL parsedUrl = new URL(url);
            return parsedUrl.getHost();
        } catch (Exception e) {
            return null; // Return null if the URL is invalid
        }
    }

//    private boolean isCrawlAllowed(String url) {
//        try {
//            URL parsedUrl = new URL(url);
//            String robotsUrl = parsedUrl.getProtocol() + "://" + parsedUrl.getHost() + "/robots.txt";
//            Document robotsDoc = Jsoup.connect(robotsUrl).get();
//            String robotsContent = robotsDoc.body().text();
////            System.out.println("robotsContent : " + robotsContent);
//
//            // Simple parsing of robots.txt
//            String[] lines = robotsContent.split("\n");
//            for (String line : lines) {
//                if (line.startsWith("User -agent: *")) {
//                    // Check for disallowed paths
//                    for (String disallow : lines) {
//                        if (disallow.startsWith("Disallow:")) {
//                            String disallowedPath = disallow.replace("Disallow:", "").trim();
//                            if (url.contains(disallowedPath)) {
//                                return false; // Crawling is not allowed for this path
//                            }
//                        }
//                    }
//                }
//            }
//        } catch (IOException e) {
//            System.err.println("Error fetching robots.txt: " + e.getMessage());
//        }
//        return true; // Default to allowed if robots.txt cannot be fetched
//    }


    private void handleIOException(String url, IOException e) {
        if (e.getMessage().contains("429")) {
            System.err.println("Error fetching URL: " + url + " - HTTP error fetching URL. Status=429");
            try {
                Thread.sleep(RETRY_DELAY_MS); // Wait before retrying
                crawl(url, new HashSet<>(), 0); // Retry crawling the same URL
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt(); // Restore interrupted status
            }
        } else if (e.getMessage().contains("500")) {
            System.err.println("Error fetching URL: " + url + " - HTTP error fetching URL. Status=500");
        } else {
            System.err.println("Error fetching URL: " + url + " - " + e.getMessage());
        }
    }
}