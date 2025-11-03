package org.estech.classify.controller;

import org.estech.classify.service.GrpcInferenceService;
import org.estech.common.dto.ClassificationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/rpc/inference")
public class GrpcInferenceController {

    private final GrpcInferenceService grpcInferenceService;

    @Autowired
    public GrpcInferenceController(GrpcInferenceService grpcInferenceService) {
        this.grpcInferenceService = grpcInferenceService;
    }

    @PostMapping(value = "/predict", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ClassificationResult predict(
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "topk", defaultValue = "5") int topk) throws Exception {
        return grpcInferenceService.predict(file, topk);
    }
}
