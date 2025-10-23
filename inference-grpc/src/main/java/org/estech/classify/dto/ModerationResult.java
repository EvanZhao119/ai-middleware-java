package org.estech.classify.dto;

import java.util.Map;

public record ModerationResult(
        String topLabel,
        double topProb,
        Map<String, Double> probs
) {}
