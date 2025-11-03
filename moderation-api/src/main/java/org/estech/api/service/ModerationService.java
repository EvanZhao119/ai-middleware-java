package org.estech.api.service;

import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;
import ai.djl.repository.zoo.ZooModel;
import lombok.extern.slf4j.Slf4j;
import org.estech.api.config.NsfwModelConfig;
import org.estech.api.dto.ModerationResult;
import org.estech.api.jni.NativeImageOps;
import org.estech.common.dto.ClassificationResult;
import org.estech.model.service.ModelService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class ModerationService {

    private final ModelService modelService = new ModelService();

    public ClassificationResult classify(MultipartFile file, int topK) throws Exception {
        try {
            // 直接从 MultipartFile 读取字节数组
            byte[] bytes = file.getBytes();

            // 调用底层模型服务
            try (InputStream input = new ByteArrayInputStream(bytes)) {
                return modelService.classify(input, topK);
            }

        } catch (Exception e) {
            log.error("Failed to classify image: {}", e.getMessage(), e);
            throw new RuntimeException("Image classification failed", e);
        }
    }

    public ClassificationResult classifyNative(MultipartFile file, int topK) throws Exception {
        byte[] bytes = file.getBytes();
        ByteBuffer encoded = ByteBuffer.allocateDirect(bytes.length);
        encoded.put(bytes);
        encoded.flip();

//        ByteBuffer chw = NativeImageOps.preprocessToCHW(encoded, 224, 224,
//                0.485f, 0.456f, 0.406f,
//                0.229f, 0.224f, 0.225f);

        ByteBuffer chw = NativeImageOps.preprocessToCHW(
                encoded,          // encodedImage
                224,              // outW
                224,              // outH
                0f, 0f, 0f,       // mean0, mean1, mean2  → 不减均值
                1f, 1f, 1f        // std0, std1, std2    → 不除标准差
        );


        try (NDManager manager = NDManager.newBaseManager()) {
            NDArray nd = manager.create(chw.asFloatBuffer(), new Shape(3, 224, 224), DataType.FLOAT32);

            log.info("NDArray min/max = " + nd.min().getFloat() + " ~ " + nd.max().getFloat());

            NativeImageOps.freeBuffer(chw);
            return modelService.classifyNDArray(nd, topK);
        }
    }

    @Async("aiTaskExecutor")
    public CompletableFuture<ClassificationResult> classifyAsync(MultipartFile file, int topK) throws Exception {
        return CompletableFuture.supplyAsync(() -> {
                    try {
                        return classify(file, topK);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                Executors.newCachedThreadPool());
    }
}

