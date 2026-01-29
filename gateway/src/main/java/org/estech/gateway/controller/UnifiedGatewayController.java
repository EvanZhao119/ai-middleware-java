package org.estech.gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.estech.gateway.model.ComputeRequest;
import org.estech.gateway.service.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
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

    @RequestMapping(value = "/v1/run", method = {RequestMethod.POST, RequestMethod.GET})
    public Mono<ResponseEntity<String>> run(
            @RequestHeader(value = "Authorization", required = false) String token,
            ServerHttpRequest request,
            ServerWebExchange exchange) {

        long start = System.nanoTime();
        String traceId = traceService.generateTraceId();

        // 1. 鉴权
        if (token == null || !securityService.validateToken(token.replace("Bearer ", ""))) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid or missing token"));
        }

        // 2. 限流
        if (activeRequests.incrementAndGet() > rateLimit) {
            activeRequests.decrementAndGet();
            return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Too many requests"));
        }

        // 3. 构建 ServerRequest
        ServerRequest serverRequest = ServerRequest.create(exchange, HandlerStrategies.withDefaults().messageReaders());
        MediaType contentType = request.getHeaders().getContentType();
        log.info("[traceId={}] Incoming request: {} {}, Content-Type: {}", traceId, request.getMethod(), request.getURI(), contentType);

        Mono<String> responseMono;

        // 4. 处理请求
        if (contentType != null && contentType.isCompatibleWith(MediaType.MULTIPART_FORM_DATA)) {
            responseMono = handleMultipart(exchange, traceId);
        } else {
            // 处理 JSON 或 GET 请求
            responseMono = serverRequest.bodyToMono(ComputeRequest.class)
                    .switchIfEmpty(Mono.defer(() -> {
                        // 针对 GET 或 Payload 为 null 的情况，从 Query Params 构建
                        ComputeRequest fallbackReq = new ComputeRequest();
                        fallbackReq.setImpl(serverRequest.queryParam("impl").orElse(null));
                        fallbackReq.setPath(serverRequest.queryParam("path").orElse(null));
                        fallbackReq.setMethod(serverRequest.queryParam("method").orElse(request.getMethod().name()));

                        // 【核心修复】：提取 URL 上除路由参数外的所有业务参数 (如 url, topK)
                        Map<String, Object> inputMap = new HashMap<>();
                        serverRequest.queryParams().forEach((key, values) -> {
                            if (!"impl".equals(key) && !"path".equals(key) && !"method".equals(key)) {
                                inputMap.put(key, values.get(0));
                            }
                        });
                        fallbackReq.setInput(inputMap);

                        if (fallbackReq.getImpl() != null && fallbackReq.getPath() != null) {
                            log.info("[traceId={}] Extracted params from Query: impl={}, path={}, business_params={}",
                                    traceId, fallbackReq.getImpl(), fallbackReq.getPath(), inputMap.keySet());
                            return Mono.just(fallbackReq);
                        }
                        return Mono.error(new RuntimeException("Empty JSON body and missing path parameters"));
                    }))
                    .flatMap(req -> handleJson(req, traceId));
        }

        // 5. 装饰熔断、监控
        return circuitService.decorate(responseMono)
                .map(ResponseEntity::ok)
                .onErrorResume(err -> {
                    metricsService.recordError(start);
                    // 如果熔断器开启，记录更详细的告警
                    if (err.getMessage().contains("OPEN")) {
                        log.error("[traceId={}] 🛡️ Circuit Breaker is OPEN. Blocking request.", traceId);
                    } else {
                        log.error("[traceId={}] Forwarding failed: {}", traceId, err.getMessage());
                    }
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err.getMessage()));
                })
                .doFinally(sig -> {
                    activeRequests.decrementAndGet();
                    metricsService.recordLatency(start);
                });
    }

    private Mono<String> handleJson(ComputeRequest req, String traceId) {
        String baseUrl = policyService.getRoute(req.getImpl());
        if (baseUrl == null) return Mono.error(new RuntimeException("Unknown service: " + req.getImpl()));

        // 路径清洗，防止双斜杠
        String targetUrl = baseUrl.replaceAll("/+$", "") + "/" + req.getPath().replaceAll("^/+", "");
        HttpMethod method = HttpMethod.valueOf(req.getMethod() != null ? req.getMethod().toUpperCase() : "POST");

        log.info("[traceId={}] → Forwarding to: {} {}", traceId, method, targetUrl);

        WebClient.RequestBodySpec requestSpec = webClient.method(method)
                .uri(uriBuilder -> {
                    java.net.URI uri = java.net.URI.create(targetUrl);
                    uriBuilder.scheme(uri.getScheme()).host(uri.getHost()).port(uri.getPort()).path(uri.getPath());

                    // GET 请求时，将 input Map 转换为 URL 参数
                    if (HttpMethod.GET.equals(method) && req.getInput() instanceof Map<?, ?> map) {
                        map.forEach((k, v) -> { if (v != null) uriBuilder.queryParam(k.toString(), v); });
                    }
                    return uriBuilder.build();
                })
                .header("X-Trace-Id", traceId);

        // 非 GET 请求添加 Body
        if (!HttpMethod.GET.equals(method)) {
            requestSpec.contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(req.getInput() != null ? req.getInput() : "");
        }

        return requestSpec.retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, resp ->
                        resp.bodyToMono(String.class).flatMap(msg ->
                                Mono.error(new RuntimeException("Downstream 4xx error: " + msg))))
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(timeoutSeconds));
    }

    private Mono<String> handleMultipart(ServerWebExchange exchange, String traceId) {
        return exchange.getMultipartData().flatMap(parts -> {
            if (parts.isEmpty()) {
                return Mono.error(new RuntimeException("Multipart data is empty (Content-Length was 0)"));
            }
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            parts.forEach((name, partList) -> {
                for (Part part : partList) {
                    if (part instanceof FilePart filePart) builder.part(name, filePart);
                    else if (part instanceof FormFieldPart fieldPart) builder.part(name, fieldPart.value());
                }
            });

            String impl = getPartValue(parts, "impl");
            String path = getPartValue(parts, "path");

            if (impl == null || path == null) return Mono.error(new IllegalArgumentException("Multipart missing impl/path"));

            String baseUrl = policyService.getRoute(impl);
            if (baseUrl == null) return Mono.error(new IllegalArgumentException("Unknown service: " + impl));

            String targetUrl = baseUrl.replaceAll("/+$", "") + "/" + path.replaceAll("^/+", "");
            log.info("[traceId={}] Forwarding Multipart → {}", traceId, targetUrl);

            return webClient.post()
                    .uri(targetUrl)
                    // 【修复点】：移除 .contentType(MediaType.MULTIPART_FORM_DATA)
                    // 让 WebClient 根据 Body 中的 Multipart 数据自动生成带 Boundary 的 Header
                    .header("X-Trace-Id", traceId)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds));
        });
    }

    private String getPartValue(org.springframework.util.MultiValueMap<String, Part> parts, String name) {
        Part part = parts.getFirst(name);
        return (part instanceof FormFieldPart fieldPart) ? fieldPart.value() : null;
    }
}