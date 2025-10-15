package org.estech.gateway.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

@Service
public class MetricsService {
    private final MeterRegistry registry;
    public MetricsService(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordLatency(String provider, long ms) {
        registry.timer("gateway.latency", "provider", provider).record(ms, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
}
