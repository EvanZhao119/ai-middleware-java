package org.estech.common.dto;

import java.util.Map;

public class ClassificationResult {
    private String label;
    private double confidence;
    private Map<String, Double> top5;

    public ClassificationResult(String label, double confidence, Map<String, Double> top5) {
        this.label = label;
        this.confidence = confidence;
        this.top5 = top5;
    }

    public String getLabel() { return label; }
    public double getConfidence() { return confidence; }
    public Map<String, Double> getTop5() { return top5; }
}
