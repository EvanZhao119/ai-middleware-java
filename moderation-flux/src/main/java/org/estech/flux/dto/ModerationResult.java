package org.estech.flux.dto;

import ai.djl.modality.Classifications;

import java.util.Map;

public record ModerationResult(String label, double score, Map<String, Double> probs) {

    public static ModerationResult from(Classifications out) {
        var best = out.best();
        Map<String, Double> probs = new java.util.LinkedHashMap<>();
        out.items().forEach(i -> probs.put(i.getClassName(), i.getProbability()));
        return new ModerationResult(best.getClassName(), best.getProbability(), probs);
    }

    public static ModerationResult timeoutFallback() {
        return new ModerationResult("timeout", 0.0, Map.of());
    }
}
