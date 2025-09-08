package org.estech.classify.service;

import org.estech.classify.client.GrpcClient;
import org.springframework.stereotype.Service;

@Service
public class GrpcInferenceService {

    private final GrpcClient grpcClient;

    public GrpcInferenceService(GrpcClient grpcClient) {
        this.grpcClient = grpcClient;
    }

    public String predict(String imagePath, int topk) throws Exception {
        return grpcClient.predict(imagePath, topk);
    }
}
