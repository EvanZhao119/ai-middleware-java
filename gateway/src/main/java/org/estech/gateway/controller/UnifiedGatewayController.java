package org.estech.gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.estech.gateway.model.ComputeRequest;
import org.estech.gateway.service.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@RestController
@RequiredArgsConstructor
public class UnifiedGatewayController {

    private final PolicyService policyService;
    private final MetricsService metricsService;
    private final TraceService traceService;
    private final SecurityService securityService;
    private final CircuitService circuitService;

    @Value("${gateway.rate-limit:100}")
    private int rateLimit;

    @Value("${gateway.timeout-seconds:30}")
    private int timeoutSeconds;

    private final AtomicInteger activeRequests = new AtomicInteger(0);
    private final WebClient webClient = WebClient.builder().build();

    @PostMapping("/v1/run")
    public Mono<ResponseEntity<String>> run(
            @RequestHeader(value = "Authorization", required = false) String token,
            ServerHttpRequest request,
            ServerWebExchange exchange) {

        long start = System.nanoTime();
        String traceId = traceService.generateTraceId();

        // ==========================================
        // 鉴权
        // ==========================================
        if (token == null || !securityService.validateToken(token.replace("Bearer ", ""))) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid or missing token"));
        }

        // ==========================================
        // 限流
        // ==========================================
        if (activeRequests.incrementAndGet() > rateLimit) {
            activeRequests.decrementAndGet();
            return Mono.just(ResponseEntity.status(429).body("Too many requests"));
        }

        // ==========================================
        // 动态判断 Content-Type
        // ==========================================
        MediaType contentType = request.getHeaders().getContentType();
        log.info("[traceId={}] Incoming content-type: {}", traceId, contentType);

        Mono<String> responseMono;

        if (contentType != null && contentType.isCompatibleWith(MediaType.MULTIPART_FORM_DATA)) {
            // 处理 multipart/form-data 上传
            responseMono = handleMultipart(exchange, traceId);
        } else if (contentType != null && contentType.isCompatibleWith(MediaType.APPLICATION_JSON)) {
            // 处理 application/json
            responseMono = exchange.getRequest().getBody()
                    .next()
                    .flatMap(dataBuffer -> {
                        try {
                            String json = dataBuffer.toString(java.nio.charset.StandardCharsets.UTF_8);
                            ComputeRequest req = new com.fasterxml.jackson.databind.ObjectMapper()
                                    .readValue(json, ComputeRequest.class);
                            return handleJson(req, traceId);
                        } catch (Exception e) {
                            return Mono.error(new RuntimeException("Invalid JSON body"));
                        }
                    });
        } else {
            responseMono = Mono.error(new RuntimeException("Unsupported Content-Type: " + contentType));
        }

        // ==========================================
        // 包装熔断、监控、日志
        // ==========================================
        return circuitService.decorate(responseMono)
                .map(body -> {
                    metricsService.recordSuccess(start);
                    return ResponseEntity.ok(body);
                })
                .onErrorResume(err -> {
                    metricsService.recordError(start);
                    log.error("[traceId={}] {}", traceId, err.getMessage());
                    return Mono.just(ResponseEntity.internalServerError().body(err.getMessage()));
                })
                .doFinally(sig -> {
                    activeRequests.decrementAndGet();
                    metricsService.recordLatency(start);
                });
    }

    private Mono<String> handleJson(ComputeRequest req, String traceId) {
        String baseUrl = policyService.getRoute(req.getImpl());
        if (baseUrl == null) {
            return Mono.just("Unknown service: " + req.getImpl());
        }

        String targetUrl = String.format("%s/%s",
                baseUrl.replaceAll("/+$", ""),
                req.getPath().replaceAll("^/+", ""));

        log.info("[traceId={}] Forwarding JSON → {}", traceId, targetUrl);

        return webClient.post()
                .uri(targetUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Trace-Id", traceId)
                .bodyValue(req.getInput())
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(timeoutSeconds));
    }

    private Mono<String> handleMultipart(ServerWebExchange exchange, String traceId) {
        return exchange.getMultipartData()
                .flatMap(parts -> {
                    org.springframework.http.codec.multipart.FormFieldPart implPart =
                            (org.springframework.http.codec.multipart.FormFieldPart) parts.getFirst("impl");
                    org.springframework.http.codec.multipart.FormFieldPart pathPart =
                            (org.springframework.http.codec.multipart.FormFieldPart) parts.getFirst("path");

                    String impl = implPart != null ? implPart.value() : null;
                    String path = pathPart != null ? pathPart.value() : null;

                    org.springframework.http.codec.multipart.FilePart file =
                            (org.springframework.http.codec.multipart.FilePart) parts.getFirst("file");

                    if (impl == null || path == null) {
                        return Mono.error(new IllegalArgumentException("Missing impl or path field"));
                    }

                    String baseUrl = policyService.getRoute(impl);
                    if (baseUrl == null) {
                        return Mono.error(new IllegalArgumentException("Unknown service: " + impl));
                    }

                    String targetUrl = String.format("%s/%s",
                            baseUrl.replaceAll("/+$", ""),
                            path.replaceAll("^/+", ""));

                    log.info("[traceId={}] Forwarding file → {}", traceId, targetUrl);

                    // 使用 WebClient 进行文件转发
                    return webClient.post()
                            .uri(targetUrl)
                            .contentType(MediaType.MULTIPART_FORM_DATA)
                            .header("X-Trace-Id", traceId)
                            .body(BodyInserters.fromMultipartData("file", file))
                            .retrieve()
                            .bodyToMono(String.class)
                            .timeout(Duration.ofSeconds(timeoutSeconds));
                });
    }

}
