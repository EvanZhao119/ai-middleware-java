package org.estech.api.dto;

import java.util.Map;

public record ModerationResult(
        String topLabel,
        double topProb,
        Map<String, Double> probs
) {}
