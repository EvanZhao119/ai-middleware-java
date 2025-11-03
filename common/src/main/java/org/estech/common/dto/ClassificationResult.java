package org.estech.common.dto;

import java.util.Map;

public class ClassificationResult {
    private String label;
    private double confidence;
    private Map<String, Double> topK;

    public ClassificationResult(String label, double confidence, Map<String, Double> topK) {
        this.label = label;
        this.confidence = confidence;
        this.topK = topK;
    }

    public String getLabel() { return label; }
    public double getConfidence() { return confidence; }
    public Map<String, Double> getTopK() { return topK; }
}
