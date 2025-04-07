package com.example.controller;

import com.example.service.CrawlerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/crawler")
public class CrawlerController {

    @Autowired
    private CrawlerService crawlerService;

    @PostMapping("/crawl")
    public Map<String, Set<String>> crawl(@RequestBody List<String> domains) {
        Map<String, CompletableFuture<Set<String>>> futureResults = new HashMap<>();

        for (String domain : domains) {
            futureResults.put(domain, crawlerService.crawl(domain));
        }

        Map<String, Set<String>> results = futureResults.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().join()));

        return results;
    }
}