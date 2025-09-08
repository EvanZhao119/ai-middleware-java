package org.estech.classify.controller;

import org.estech.classify.service.GrpcInferenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inference")
public class GrpcInferenceController {

    private final GrpcInferenceService grpcInferenceService;

    @Autowired
    public GrpcInferenceController(GrpcInferenceService grpcInferenceService) {
        this.grpcInferenceService = grpcInferenceService;
    }

    @GetMapping("/predict")
    public String predict(
            @RequestParam("imagePath") String imagePath,
            @RequestParam(name = "topk", defaultValue = "5") int topk) throws Exception {
        return grpcInferenceService.predict(imagePath, topk);
    }
}
