package org.estech.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.estech.api.dto.ModerationResult;
import org.estech.api.service.ModerationService;
import org.estech.common.dto.ClassificationResult;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Tag(name = "NSFW")
@RestController
@RequestMapping("/api/moderation")
public class ModerationController {

    private final ModerationService service;

    public ModerationController(ModerationService service) {
        this.service = service;
    }

    @Operation(summary = "Synchorizing: Upload Image")
    @PostMapping(value = "/classify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ClassificationResult classify(@RequestPart("file") MultipartFile file,
                                         @RequestParam(defaultValue = "5") int topK) throws Exception {
        return service.classify(file, topK);
    }

    @Operation(summary = "Asynchronizing: Upload Image")
    @PostMapping(value = "/classify-async", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CompletableFuture<ClassificationResult> classifyAsync(@RequestPart("file") MultipartFile file,
                                                             @RequestParam(defaultValue = "5") int topK) throws Exception {
        return service.classifyAsync(file, topK);
    }

    @PostMapping("/native")
    public ClassificationResult classifyNative(@RequestParam("file") MultipartFile file,
                                           @RequestParam(defaultValue = "5") int topK) throws Exception {
        return service.classifyNative(file, topK);
    }

}
