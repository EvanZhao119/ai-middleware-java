package org.estech.flux.model;

import lombok.Data;

@Data
public class ResearchEvidence {
    private String paperUrl;
    private String paperTitle;
    private String sensorModality;
    private String environmentContext;
    private String keyMetrics;
    private String benchmarkResults;
    private String evidenceSummary;
    private String sourceQuote;
}
