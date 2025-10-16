package org.estech.gateway.service;

import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class TraceService {
    public String generateTraceId() {
        return UUID.randomUUID().toString();
    }
}
