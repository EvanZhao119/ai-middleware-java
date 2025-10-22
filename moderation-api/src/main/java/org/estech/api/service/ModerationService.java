package org.estech.api.service;

import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;
import ai.djl.repository.zoo.ZooModel;
import org.estech.api.config.NsfwModelConfig;
import org.estech.api.dto.ModerationResult;
import org.estech.api.jni.NativeImageOps;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
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

            BufferedImage img = ImageIO.read(in);
            NDArray nd = NsfwModelConfig.preprocess(img, model.getNDManager());
            NDList input = new NDList(nd);

            Classifications out = predictor.predict(input);

            var best = out.best();
            Map<String, Double> probs = new LinkedHashMap<>();
            out.items().forEach(i -> probs.put(i.getClassName(), i.getProbability()));

            return new ModerationResult(best.getClassName(), best.getProbability(), probs);
        }
    }

    public ModerationResult classifyNative(MultipartFile file) throws Exception {
        try (Predictor<NDList, Classifications> predictor = model.newPredictor()) {

            byte[] bytes = file.getBytes();
            ByteBuffer encoded = ByteBuffer.allocateDirect(bytes.length);
            encoded.put(bytes);
            encoded.flip();

            NDManager manager = model.getNDManager();

            ByteBuffer chw = NativeImageOps.preprocessToCHW(encoded, 224, 224,
                    0.485f, 0.456f, 0.406f,
                    0.229f, 0.224f, 0.225f);
            NDArray nd = manager.create(chw.asFloatBuffer(), new Shape(1, 3, 224, 224), DataType.FLOAT32);
            NativeImageOps.freeBuffer(chw);

            NDList input = new NDList(nd);
            Classifications out = predictor.predict(input);

            Map<String, Double> probs = new LinkedHashMap<>();
            out.topK(5).forEach(c -> probs.put(c.getClassName(), c.getProbability()));

            var best = out.best();
            return new ModerationResult(best.getClassName(), best.getProbability(), probs);
        }
    }

    @Async("aiTaskExecutor")
    public CompletableFuture<ModerationResult> classifyAsync(MultipartFile file) throws Exception {
        return CompletableFuture.completedFuture(classify(file));
    }
}

