package org.estech.gateway.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RouterService {
    private final PolicyService policyService;

    public String resolveUrl(String impl) {
        String url = policyService.getServiceUrl(impl);
        if (url == null)
            throw new IllegalArgumentException("Unknown implementation: " + impl);
        return url;
    }
}
