package org.estech.flux.service;

import lombok.extern.slf4j.Slf4j;
import org.estech.common.dto.ClassificationResult;
import org.estech.model.service.ModelService;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Slf4j
@Service
public class ModerationFluxService {

    private final ModelService modelService = new ModelService();

    public Mono<ClassificationResult> classify(FilePart file, int topK) {
        return DataBufferUtils.join(file.content())  // 合并所有 DataBuffer
                .flatMap(dataBuffer -> {
                    try {
                        // 读取所有字节
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);  // 释放资源

                        // 调用 ModelService
                        return Mono.fromCallable(() -> {
                            try (InputStream input = new ByteArrayInputStream(bytes)) {
                                return modelService.classify(input, topK);
                            }
                        }).subscribeOn(Schedulers.boundedElastic());

                    } catch (Exception e) {
                        DataBufferUtils.release(dataBuffer);
                        return Mono.error(e);
                    }
                });
    }

    public Mono<ClassificationResult> classify(FilePart file) {
        return classify(file, 5);
    }

}
