package org.estech.flux.controller;


import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.estech.flux.dto.ModerationResult;
import org.estech.flux.service.ModerationFluxService;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/flux/moderation")
@RequiredArgsConstructor
public class ModerationFluxController {
    private final ModerationFluxService service;
    private final MeterRegistry meterRegistry;

    @Timed(value = "model.inference.time", description = "Time taken for model inference")
    @PostMapping(value = "/check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ModerationResult> check(@RequestPart("file") FilePart file) {
        long start = System.nanoTime();
        return service.classify(file)
                .doOnSuccess(result -> {
                    meterRegistry.counter("model.inference.success").increment();
                })
                .doOnError(err -> {
                    meterRegistry.counter("model.inference.error").increment();
                })
                .doFinally(signal -> {
                    long duration = System.nanoTime() - start;
                    meterRegistry.timer("model.inference.latency")
                            .record(duration, TimeUnit.NANOSECONDS);
                });
    }
}
