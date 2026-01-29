package org.estech.flux.controller;

import org.estech.flux.model.ResearchEvidence;
import org.estech.flux.service.ResearchAIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/flux/intelligence")
public class IntelligenceController {

    @Autowired
    private ResearchAIService aiService;

    @GetMapping("/analyze-by-url")
    public Mono<ResearchEvidence> analyzePaper(@RequestParam String url) {
        return aiService.fetchAndParsePdf(url)
                .flatMap(aiService::extractIntelligence)
                .doOnNext(evidence -> aiService.saveToHistory(url, evidence));
    }

    @GetMapping("/history")
    public List<Map<String, Object>> getHistory() {
        return aiService.getHistory();
    }
}
