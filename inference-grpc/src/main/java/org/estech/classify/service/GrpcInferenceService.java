package org.estech.classify.service;

import org.estech.classify.client.GrpcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class GrpcInferenceService {

    private final GrpcClient grpcClient;

    public GrpcInferenceService(GrpcClient grpcClient) {
        this.grpcClient = grpcClient;
    }

    public String predict(MultipartFile file, int topk) throws Exception {
        return grpcClient.predict(file, topk);
    }
}
