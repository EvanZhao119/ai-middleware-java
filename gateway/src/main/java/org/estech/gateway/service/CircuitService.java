package org.estech.gateway.service;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.core.IntervalFunction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.retry.RetryBackoffSpec;

import java.time.Duration;

@Slf4j
@Service
public class CircuitService {

    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final RetryConfig retryConfig;

    public CircuitService(
            @Value("${gateway.retry-attempts:3}") int retries,
            @Value("${gateway.circuit-failure-threshold:3}") int failureThreshold
    ) {
        // 熔断器配置
        this.circuitBreaker = CircuitBreaker.of("gateway",
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(50f)
                        .minimumNumberOfCalls(failureThreshold)
                        .waitDurationInOpenState(Duration.ofSeconds(10))
                        .enableAutomaticTransitionFromOpenToHalfOpen()
                        .build());

        IntervalFunction intervalFn = IntervalFunction.of(Duration.ofSeconds(1));

        this.retryConfig = RetryConfig.custom()
                .maxAttempts(retries)
                .intervalFunction(intervalFn)
                .retryExceptions(Exception.class)
                .build();

        this.retry = Retry.of("gateway", retryConfig);
    }

    /**
     * 包装 Mono 任务：支持 Resilience4j 熔断 + Reactor 重试
     */
    public <T> Mono<T> decorate(Mono<T> source) {
        int maxAttempts = retryConfig.getMaxAttempts();

        long delayMillis = retryConfig.getIntervalFunction().apply(1);
        Duration delay = Duration.ofMillis(delayMillis);

        RetryBackoffSpec reactorRetry = reactor.util.retry.Retry
                .fixedDelay(maxAttempts, delay)
                .filter(throwable -> true)
                .onRetryExhaustedThrow((spec, signal) -> signal.failure());

        return source
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .retryWhen(reactorRetry)
                .doOnError(e -> log.warn("⚠️ Retry failed after {} attempts: {}", maxAttempts, e.getMessage()));
    }
}
