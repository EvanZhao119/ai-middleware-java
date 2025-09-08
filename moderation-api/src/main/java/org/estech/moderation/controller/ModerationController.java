package org.estech.moderation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.estech.moderation.dto.ModerationResult;
import org.estech.moderation.service.ModerationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
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
