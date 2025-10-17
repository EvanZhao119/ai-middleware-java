package org.estech.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.estech.api.dto.ModerationResult;
import org.estech.api.service.ModerationService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.CompletableFuture;

@Tag(name = "NSFW")
@RestController
@RequestMapping("/api/moderation")
public class ModerationController {

    private final ModerationService service;

    public ModerationController(ModerationService service) {
        this.service = service;
    }

    @Operation(summary = "Synchorizing: Upload Image")
    @PostMapping(value = "/check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ModerationResult check(@RequestPart("file") MultipartFile file) throws Exception {
        System.out.println("/check request ============= ");
        return service.classify(file);
    }

    @Operation(summary = "Asynchronizing: Upload Image")
    @PostMapping(value = "/check-async", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CompletableFuture<ModerationResult> checkAsync(@RequestPart("file") MultipartFile file) throws Exception {
        System.out.println("/check-async request ============= ");
        return service.classifyAsync(file);
    }

}
