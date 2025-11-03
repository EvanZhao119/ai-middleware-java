package org.estech.api.service;

import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import lombok.extern.slf4j.Slf4j;
import org.estech.api.jni.NativeImageOps;
import org.estech.common.dto.ClassificationResult;
import org.estech.model.service.ModelService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.estech.common.constants.ModelConstants;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class ModerationService {

    private final ModelService modelService = new ModelService();

    private final NativeImageOps nativeOps = new NativeImageOps();

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
        log.info("Processing image with native preprocessing: {}, topK={}",
                file.getOriginalFilename(), topK);

        // 1. 读取上传文件到 DirectByteBuffer
        byte[] imageBytes = file.getBytes();
        ByteBuffer encodedImage = ByteBuffer.allocateDirect(imageBytes.length);
        encodedImage.put(imageBytes);
        encodedImage.flip();

        log.info("Image loaded: {} bytes", imageBytes.length);

        // 2. 调用 Native 方法预处理
        float[] chwData = nativeOps.preprocessImageNetCHW(
                encodedImage,
                ModelConstants.IMAGE_SIZE
        );

        // 3. 验证数据
        NativeImageOps.validateAndPrintStats(chwData, "Native preprocessed data");

        if (chwData == null) {
            throw new RuntimeException("Native preprocessing returned null");
        }

        int expectedSize = 3 * ModelConstants.IMAGE_SIZE * ModelConstants.IMAGE_SIZE;
        if (chwData.length != expectedSize) {
            throw new RuntimeException(
                    String.format("Invalid array size: expected %d, got %d",
                            expectedSize, chwData.length));
        }

        // 4. 转换为 NDArray 并预测
        try (NDManager manager = NDManager.newBaseManager()) {
            // 创建 NDArray: shape = [3, H, W]
            NDArray array = manager.create(chwData,
                    new Shape(3, ModelConstants.IMAGE_SIZE, ModelConstants.IMAGE_SIZE));

            log.info("NDArray created: shape={}, dataType={}",
                    array.getShape(), array.getDataType());

            // 验证 NDArray 数据
            validateNDArray(array);

            // 5. 添加 batch 维度: [3, H, W] -> [1, 3, H, W]
//            NDArray batched = array.expandDims(0);
//            log.info("Batched NDArray shape: {}", batched.getShape());

            // 6. 使用 ModelService 进行预测
            ClassificationResult result = modelService.classifyNDArray(array, topK);

            log.info("Classification completed. Top prediction: {} (confidence: {})",
                    result.getLabel(), result.getConfidence());

            return result;
        }
    }

    /**
     * 验证 NDArray 数据有效性
     */
    private void validateNDArray(NDArray array) {
        float min = array.min().getFloat();
        float max = array.max().getFloat();
        float mean = array.mean().getFloat();

        log.info("NDArray validation: min={}, max={}, mean={}", min, max, mean);

        if (Float.isNaN(min) || Float.isNaN(max)) {
            throw new RuntimeException("NDArray contains NaN values");
        }

        if (Float.isInfinite(min) || Float.isInfinite(max)) {
            throw new RuntimeException("NDArray contains Infinite values");
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

