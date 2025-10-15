package org.estech.gateway.service;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

@Service
@Getter
public class PolicyService {
    private Map<String, Object> policy;

    @PostConstruct
    public void loadPolicy() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("policy.yaml")) {
            this.policy = new Yaml().load(in);
            System.out.println("[Gateway] policy.yaml loaded: " + policy);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load policy.yaml", e);
        }
    }

    public String getServiceUrl(String name) {
        Map<String, String> providers = (Map<String, String>) policy.get("providers");
        return providers.get(name);
    }
}
