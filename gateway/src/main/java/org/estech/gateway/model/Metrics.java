package org.estech.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Metrics {
    private long latencyMs;
    private String provider;
    private double costPer1k;
}
