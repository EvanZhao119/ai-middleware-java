package org.estech.gateway.service;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class MetricsService {

    private final MeterRegistry meterRegistry;

    public void recordSuccess(long startNano) {
        meterRegistry.counter("gateway.run.success").increment();
        recordLatency(startNano);
    }

    public void recordError(long startNano) {
        meterRegistry.counter("gateway.run.error").increment();
        recordLatency(startNano);
    }

    public void recordLatency(long startNano) {
        long duration = System.nanoTime() - startNano;
        meterRegistry.timer("gateway.run.latency")
                .record(duration, TimeUnit.NANOSECONDS);
    }
}
