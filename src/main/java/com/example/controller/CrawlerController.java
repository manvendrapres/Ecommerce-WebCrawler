package com.example.controller;

import com.example.service.CrawlerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/crawler")
public class CrawlerController {

    Logger logger = LoggerFactory.getLogger(CrawlerController.class);

    @Autowired
    private CrawlerService crawlerService;

    @PostMapping("/crawl")
    public Map<String, Set<String>> crawl(@RequestBody List<String> domains) {
//        Map<String, CompletableFuture<Set<String>>> futureResults = new HashMap<>();
//
//        for (String domain : domains) {
//            futureResults.put(domain, crawlerService.crawl(domain));
//        }
//
//        Map<String, Set<String>> results = new HashMap<>();
//        futureResults.forEach((domain, future) -> results.put(domain, future.join()));
//
//        return results;

        return domains.parallelStream()  // Parallelize domain-wise
                .collect(Collectors.toMap(
                        domain -> domain,
                        domain -> {
                            try {
                                return crawlerService.crawl(domain).join();
                            } catch (Exception e) {
                                logger.error("Failed crawling {}", domain, e);
                                return Set.of();
                            }
                        }
                ));
    }
}