package org.estech.moderation.service;

import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.repository.zoo.ZooModel;
import org.estech.moderation.config.NsfwModelConfig;
import org.estech.moderation.dto.ModerationResult;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class ModerationService {

    private final ZooModel<NDList, Classifications> model;

    public ModerationService(ZooModel<NDList, Classifications> model) {
        this.model = model;
    }

    public ModerationResult classify(MultipartFile file) throws Exception {
        try (InputStream in = file.getInputStream();
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

            System.out.println("[NSFW] Predict: " + best.getClassName() + " (" + best.getProbability() + ")");
            System.out.println("[NSFW] Time: " + durationMs + " ms");

            return new ModerationResult(best.getClassName(), best.getProbability(), probs);
        }
    }

    @Async("aiTaskExecutor")
    public CompletableFuture<ModerationResult> classifyAsync(MultipartFile file) throws Exception {
        return CompletableFuture.completedFuture(classify(file));
    }
}

