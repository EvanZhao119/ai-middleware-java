package org.estech.classify.client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.estech.classify.ClassifierGrpc;
import org.estech.classify.ImageRequest;
import org.estech.classify.Prediction;
import org.estech.classify.PredictionResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@Component
public class GrpcClient {

    private final ManagedChannel channel;
    private final ClassifierGrpc.ClassifierBlockingStub blockingStub;

    public GrpcClient(@Value("${grpc.server.host}") String host,
                      @Value("${grpc.server.port}") int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.blockingStub = ClassifierGrpc.newBlockingStub(channel);
    }

    public PredictionResponse predict(MultipartFile file, int topK) throws IOException {
        byte[] data = file.getBytes();

        ImageRequest request = ImageRequest.newBuilder()
                .setImage(com.google.protobuf.ByteString.copyFrom(data))
                .setTopk(topK)
                .build();

        PredictionResponse resp;
        try {
            resp = blockingStub
                    .withDeadlineAfter(10, java.util.concurrent.TimeUnit.SECONDS)
                    .predict(request);
        } catch (StatusRuntimeException e) {
            log.error("gRPC predict failed: {}", e.getStatus(), e);
            throw e; // or return structured error
        }

        return resp;
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdownNow();
        }
    }
}
