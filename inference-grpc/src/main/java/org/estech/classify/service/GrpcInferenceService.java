package org.estech.classify.service;

import org.estech.classify.Prediction;
import org.estech.classify.PredictionResponse;
import org.estech.classify.client.GrpcClient;
import org.estech.classify.dto.ModerationResult;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class GrpcInferenceService {

    private final GrpcClient grpcClient;

    public GrpcInferenceService(GrpcClient grpcClient) {
        this.grpcClient = grpcClient;
    }

    public ModerationResult predict(MultipartFile file, int topk) throws Exception {
        PredictionResponse resp = grpcClient.predict(file, topk);
        Map<String, Double> probs = new LinkedHashMap<>();
        for (Prediction p : resp.getTopkList()) {
            probs.put(p.getLabel(), (double)p.getProb());
        }

        var best = probs.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElseThrow();

        return new ModerationResult(best.getKey(), best.getValue(), probs);
    }
}
