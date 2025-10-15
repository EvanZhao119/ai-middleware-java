package org.estech.gateway.controller;

import lombok.RequiredArgsConstructor;
import org.estech.gateway.model.ComputeResponse;
import org.estech.gateway.model.Metrics;
import org.estech.gateway.service.PolicyService;
import org.estech.gateway.service.RouterService;
import org.estech.gateway.service.TraceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ComputeController {
    private final RouterService routerService;
    private final PolicyService policyService;
    private final TraceService traceService;
    private final WebClient webClient = WebClient.builder().build();

    @GetMapping("/compute")
    public Mono<ComputeResponse> compute(@RequestParam String impl,
                                         @RequestParam String input) {

        long start = System.currentTimeMillis();
        String requestId = traceService.newRequestId();

        // 从 policy.yaml 获取目标 URL
        String targetUrl = routerService.resolveUrl(impl);

        return webClient.get()
                .uri(targetUrl + "?input=" + input)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .map(result -> {
                    long latency = System.currentTimeMillis() - start;
                    return new ComputeResponse(
                            requestId,
                            Map.of("summary", result),
                            new Metrics(latency, impl, 0.0),
                            traceService.traceUrl(requestId)
                    );
                });
    }
}
