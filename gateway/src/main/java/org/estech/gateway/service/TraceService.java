package org.estech.gateway.service;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TraceService {

    public String newRequestId() {
        return "req_" + UUID.randomUUID().toString().substring(0, 8);
    }

    public String traceUrl(String requestId) {
        return "http://localhost:8080/trace/" + requestId;
    }
}
