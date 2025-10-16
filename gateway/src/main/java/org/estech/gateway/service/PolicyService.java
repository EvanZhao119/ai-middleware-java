package org.estech.gateway.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.Map;

@Slf4j
@Service
@Getter
public class PolicyService {

    @Value("classpath:policy.yaml")
    private Resource policyResource;

    private Map<String, String> routes;

    @PostConstruct
    public void loadPolicy() {
        try (InputStream input = policyResource.getInputStream()) {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(input);
            this.routes = (Map<String, String>) data.get("routes");
            log.info("Loaded routes: {}", routes);
        } catch (Exception e) {
            log.error("Failed to load policy.yaml", e);
        }
    }

    public String getRoute(String service) {
        return routes.get(service);
    }
}
