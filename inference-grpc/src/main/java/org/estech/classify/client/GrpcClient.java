package org.estech.classify.client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.estech.classify.ClassifierGrpc;
import org.estech.classify.ImageRequest;
import org.estech.classify.Prediction;
import org.estech.classify.PredictionResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

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

    private static byte[] readAllBytes(String path) throws IOException {
        return Files.readAllBytes(Paths.get(path));
    }

    public String predict(String imagePath, int topk) throws IOException {
        byte[] data = readAllBytes(imagePath);

        ImageRequest request = ImageRequest.newBuilder()
                .setImage(com.google.protobuf.ByteString.copyFrom(data))
                .setTopk(topk)
                .build();

        PredictionResponse resp;
        try {
            resp = blockingStub
                    .withDeadlineAfter(10, java.util.concurrent.TimeUnit.SECONDS)
                    .predict(request);
        } catch (StatusRuntimeException e) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (Prediction p : resp.getTopkList()) {
            sb.append(String.format("%-30s %.4f%n", p.getLabel(), p.getProb()));
        }
        return sb.toString();
    }

    public void shutdown() {
        channel.shutdown();
    }
}
