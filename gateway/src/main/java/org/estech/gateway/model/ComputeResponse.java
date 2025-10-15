package org.estech.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ComputeResponse {
    private String requestId;
    private Map<String, Object> output;
    private Metrics metrics;
    private String traceUrl;
}
