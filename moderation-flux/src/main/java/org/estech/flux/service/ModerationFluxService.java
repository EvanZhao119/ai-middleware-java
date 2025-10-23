package org.estech.flux.service;

import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.repository.zoo.ZooModel;
import lombok.extern.slf4j.Slf4j;
import org.estech.flux.config.NsfwModelConfig;
import org.estech.flux.dto.ModerationResult;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
public class ModerationFluxService {

    private final ZooModel<NDList, Classifications> model;

    public ModerationFluxService(ZooModel<NDList, Classifications> model) {
        this.model = model;
    }

    public Mono<ModerationResult> classify(FilePart file) {
        return DataBufferUtils.join(file.content())
                .flatMap(buffer -> Mono.fromCallable(() -> {
                    try (InputStream in = buffer.asInputStream();
                         Predictor<NDList, Classifications> predictor = model.newPredictor()) {

                        long start = System.nanoTime();

                        BufferedImage img = ImageIO.read(in);

                        NDArray nd = NsfwModelConfig.preprocess(img, model.getNDManager());
                        NDList input = new NDList(nd);

                        Classifications out = predictor.predict(input);

                        var best = out.best();
                        Map<String, Double> probs = new LinkedHashMap<>();
                        out.items().forEach(i -> probs.put(i.getClassName(), i.getProbability()));

                        long end = System.nanoTime();
                        long durationMs = (end - start) / 1_000_000;

                        log.info("[NSFW] Predict: " + best.getClassName() + " (" + best.getProbability() + ")");
                        log.info("[NSFW] Time: " + durationMs + " ms");

                        return new ModerationResult(best.getClassName(), best.getProbability(), probs);
                    }
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofSeconds(3))
                .onErrorResume(TimeoutException.class,
                        e -> Mono.just(ModerationResult.timeoutFallback()));
    }
}
